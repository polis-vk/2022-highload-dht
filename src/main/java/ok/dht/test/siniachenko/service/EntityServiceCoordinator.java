package ok.dht.test.siniachenko.service;

import ok.dht.ServiceConfig;
import ok.dht.test.siniachenko.Utils;
import ok.dht.test.siniachenko.exception.BadRequestException;
import ok.dht.test.siniachenko.exception.NotEnoughReplicasException;
import ok.dht.test.siniachenko.exception.ServiceInternalErrorException;
import ok.dht.test.siniachenko.exception.ServiceUnavailableException;
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
import java.util.function.Supplier;

public class EntityServiceCoordinator implements EntityService {
    private static final Logger LOG = LoggerFactory.getLogger(EntityServiceCoordinator.class);

    public static final String NOT_ENOUGH_REPLICAS_RESULT_CODE = "504 Not Enough Replicas";

    private final ServiceConfig config;
    private final DB levelDb;
    private final HttpClient httpClient;
    private final NodeTaskManager nodeTaskManager;
    private final NodeMapper nodeMapper;
    private final int defaultFromCount;
    private final int defaultAckCount;

    public EntityServiceCoordinator(
        ServiceConfig config, DB levelDb,
        HttpClient httpClient, NodeTaskManager nodeTaskManager
    ) {
        this.config = config;
        this.levelDb = levelDb;
        this.httpClient = httpClient;
        this.nodeTaskManager = nodeTaskManager;
        this.nodeMapper = new NodeMapper(config.clusterUrls());
        this.defaultFromCount = config.clusterUrls().size();
        this.defaultAckCount = config.clusterUrls().size() / 2 + 1;
    }

    @Override
    public Response handleGet(Request request, String id) {
        Supplier<byte[]> localWork = () -> {
            try {
                return levelDb.get(Utf8.toBytes(id));
            } catch (DBException e) {
                LOG.error("Error in DB", e);
                throw e;
            }
        };

        byte[][] bodies;
        try {
            bodies = replicateRequestAndAwait(request, id, localWork);
        } catch (NotEnoughReplicasException e) {
            return new Response(NOT_ENOUGH_REPLICAS_RESULT_CODE, Response.EMPTY);
        } catch (ServiceInternalErrorException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        } catch (BadRequestException e) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        } catch (ServiceUnavailableException e) {
            return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
        }

        return aggregateGetResults(bodies);
    }

    private Response aggregateGetResults(byte[][] bodies) {
        byte[] latestBody = null;
        long maxTimeMillis = 0;
        for (byte[] body : bodies) {
            if (body != null && body.length != 0) {
                long timeMillis = Utils.readTimeMillisFromBytes(body);
                if (maxTimeMillis < timeMillis) {
                    maxTimeMillis = timeMillis;
                    latestBody = body;
                }
            }
        }
        if (latestBody == null || Utils.readFlagDeletedFromBytes(latestBody)) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        } else {
            byte[] realValue = Utils.readValueFromBytes(latestBody);
            return new Response(Response.OK, realValue);
        }
    }

    @Override
    public Response handlePut(Request request, String id) {
        // coordinator saves its current time millis and false deleted flag in request body
        request.setBody(
            Utils.withCurrentTimestampAndFlagDeleted(request.getBody(), false)
        );

        Supplier<byte[]> localWork = () -> {
            try {
                levelDb.put(Utf8.toBytes(id), request.getBody());
            } catch (DBException e) {
                LOG.error("Error in DB", e);
                throw e;
            }
            return null;
        };

        try {
            replicateRequestAndAwait(request, id, localWork);
            return new Response(Response.CREATED, Response.EMPTY);
        } catch (NotEnoughReplicasException e) {
            return new Response(NOT_ENOUGH_REPLICAS_RESULT_CODE, Response.EMPTY);
        } catch (ServiceInternalErrorException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        } catch (BadRequestException e) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        } catch (ServiceUnavailableException e) {
            return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
        }
    }

    @Override
    public Response handleDelete(Request request, String id) {
        // coordinator saves its current time millis and true deleted flag in request body
        request.setBody(
            Utils.withCurrentTimestampAndFlagDeleted(request.getBody(), true)
        );

        Supplier<byte[]> localWork = () -> {
            try {
                levelDb.put(Utf8.toBytes(id), request.getBody());
            } catch (DBException e) {
                LOG.error("Error in DB", e);
                throw e;
            }
            return null;
        };

        try {
            replicateRequestAndAwait(request, id, localWork);
            return new Response(Response.ACCEPTED, Response.EMPTY);
        } catch (NotEnoughReplicasException e) {
            return new Response(NOT_ENOUGH_REPLICAS_RESULT_CODE, Response.EMPTY);
        } catch (ServiceInternalErrorException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        } catch (BadRequestException e) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        } catch (ServiceUnavailableException e) {
            return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
        }
    }

    private byte[][] replicateRequestAndAwait(
        Request request, String id, Supplier<byte[]> localWork
    ) throws ServiceInternalErrorException, NotEnoughReplicasException,
        BadRequestException, ServiceUnavailableException {
        final int ack = getIntParameter(request, "ack=", defaultAckCount);
        final int from = getIntParameter(request, "from=", defaultFromCount);
        if (!(
            0 < from && from <= config.clusterUrls().size()
                && 0 < ack && ack <= config.clusterUrls().size()
                && ack <= from
        )) {
            throw new BadRequestException();
        }

        return new ReplicatedRequestExecutor(request, id, localWork, ack, from).execute(
            config.selfUrl(), nodeMapper, nodeTaskManager, httpClient
        );
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
