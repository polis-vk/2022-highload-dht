package ok.dht.test.siniachenko.service;

import ok.dht.ServiceConfig;
import ok.dht.test.siniachenko.Utils;
import ok.dht.test.siniachenko.exception.NotEnoughReplicasException;
import ok.dht.test.siniachenko.nodemapper.NodeMapper;
import ok.dht.test.siniachenko.nodetaskmanager.NodeTaskManager;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.util.Utf8;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

public class EntityServiceCoordinator implements AsyncEntityService {
    private static final Logger LOG = LoggerFactory.getLogger(EntityServiceCoordinator.class);

    public static final String NOT_ENOUGH_REPLICAS_RESULT_CODE = "504 Not Enough Replicas";
    public static final String ERROR_IN_DB_MESSAGE = "Error in DB";

    private final ServiceConfig config;
    private final DB levelDb;
    private final HttpClient httpClient;
    private final ExecutorService executorService;
    private final NodeTaskManager nodeTaskManager;
    private final NodeMapper nodeMapper;
    private final int defaultFromCount;
    private final int defaultAckCount;

    public EntityServiceCoordinator(
        ServiceConfig config, DB levelDb, ExecutorService executorService,
        HttpClient httpClient, NodeTaskManager nodeTaskManager
    ) {
        this.config = config;
        this.levelDb = levelDb;
        this.executorService = executorService;
        this.httpClient = httpClient;
        this.nodeTaskManager = nodeTaskManager;
        this.nodeMapper = new NodeMapper(config.clusterUrls());
        this.defaultFromCount = config.clusterUrls().size();
        this.defaultAckCount = config.clusterUrls().size() / 2 + 1;
    }

    @Override
    public CompletableFuture<Response> handleGet(Request request, String id) {
        Supplier<byte[]> localWork = () -> {
            try {
                return levelDb.get(Utf8.toBytes(id));
            } catch (DBException e) {
                LOG.error(ERROR_IN_DB_MESSAGE, e);
                throw e;
            }
        };

        return replicateRequestAsync(request, id, localWork, EntityServiceCoordinator::aggregateGetResults);
    }

    private static Response aggregateGetResults(byte[][] values) {
        byte[] latestValue = null;
        long maxTimeMillis = 0;
        for (byte[] value : values) {
            if (value != null && value.length != 0) {
                long timeMillis = Utils.readTimeMillisFromBytes(value);
                if (maxTimeMillis < timeMillis) {
                    maxTimeMillis = timeMillis;
                    latestValue = value;
                }
            }
        }
        if (latestValue == null || Utils.readFlagDeletedFromBytes(latestValue)) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        } else {
            byte[] realValue = Utils.readValueFromBytes(latestValue);
            return new Response(Response.OK, realValue);
        }
    }

    @Override
    public CompletableFuture<Response> handlePut(Request request, String id) {
        // coordinator saves its current time millis and false deleted flag in request body
        request.setBody(
            Utils.withCurrentTimestampAndFlagDeleted(request.getBody(), false)
        );

        Supplier<byte[]> localWork = () -> {
            try {
                levelDb.put(Utf8.toBytes(id), request.getBody());
            } catch (DBException e) {
                LOG.error(ERROR_IN_DB_MESSAGE, e);
                throw e;
            }
            return null;
        };

        return replicateRequestAsync(request, id, localWork, b -> new Response(Response.CREATED, Response.EMPTY));
    }

    @Override
    public CompletableFuture<Response> handleDelete(Request request, String id) {
        // coordinator saves its current time millis and true deleted flag in request body
        request.setBody(
            Utils.withCurrentTimestampAndFlagDeleted(request.getBody(), true)
        );

        Supplier<byte[]> localWork = () -> {
            try {
                levelDb.put(Utf8.toBytes(id), request.getBody());
            } catch (DBException e) {
                LOG.error(ERROR_IN_DB_MESSAGE, e);
                throw e;
            }
            return null;
        };

        return replicateRequestAsync(request, id, localWork, b -> new Response(Response.ACCEPTED, Response.EMPTY));
    }

    private CompletableFuture<Response> replicateRequestAsync(
        Request request, String id, Supplier<byte[]> localWork, Function<byte[][], Response> onSuccess
    ) {
        final int ack = getIntParameter(request, "ack=", defaultAckCount);
        final int from = getIntParameter(request, "from=", defaultFromCount);
        if (!(
            0 < from && from <= config.clusterUrls().size()
                && 0 < ack && ack <= config.clusterUrls().size()
                && ack <= from
        )) {
            return CompletableFuture.completedFuture(new Response(Response.BAD_REQUEST, Response.EMPTY));
        }

        ReplicatedRequestExecutor replicatedRequestExecutor = new ReplicatedRequestExecutor(
            executorService, request, id, localWork, ack, from
        );

        return replicatedRequestExecutor.execute(config.selfUrl(), nodeMapper, nodeTaskManager, httpClient)
            .thenApply(onSuccess)
            .exceptionally(completionException -> {
                Throwable cause = completionException.getCause();
                if (cause instanceof NotEnoughReplicasException) {
                    return new Response(NOT_ENOUGH_REPLICAS_RESULT_CODE, Response.EMPTY);
                } else { // cause can be an instance of ServiceInternalErrorException or other unexpected Exception
                    return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
                }
            });
    }

    private static int getIntParameter(Request request, String parameter, int defaultValue) {
        int value;
        String gotParameter = request.getParameter(parameter);
        if (gotParameter == null || gotParameter.isEmpty()) {
            value = defaultValue;
        } else {
            try {
                value = Integer.parseInt(gotParameter);
            } catch (NumberFormatException e) {
                value = -1;
            }
        }
        return value;
    }
}
