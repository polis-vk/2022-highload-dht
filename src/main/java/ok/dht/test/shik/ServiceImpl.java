package ok.dht.test.shik;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.shik.consistency.InconsistencyStrategyType;
import ok.dht.test.shik.consistency.RepairResolutionStrategy;
import ok.dht.test.shik.events.HandlerDigestRequest;
import ok.dht.test.shik.events.HandlerLeaderResponse;
import ok.dht.test.shik.events.HandlerRangeRequest;
import ok.dht.test.shik.events.HandlerRepairRequest;
import ok.dht.test.shik.events.HandlerRequest;
import ok.dht.test.shik.events.HandlerResponse;
import ok.dht.test.shik.events.LeaderRequestState;
import ok.dht.test.shik.model.DBValue;
import ok.dht.test.shik.serialization.ByteArraySerializer;
import ok.dht.test.shik.serialization.ByteArraySerializerFactory;
import ok.dht.test.shik.sharding.HashingAlgorithm;
import ok.dht.test.shik.sharding.ShardingConfig;
import ok.dht.test.shik.streaming.ChunkedResponse;
import ok.dht.test.shik.utils.ServiceUtils;
import ok.dht.test.shik.workers.WorkersConfig;
import one.nio.http.Response;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.Iq80DBFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ServiceImpl implements CustomService {

    private static final Log LOG = LogFactory.getLog(ServiceImpl.class);
    private static final Options LEVELDB_OPTIONS = new Options();
    private static final String NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";

    private final ServiceConfig config;
    private final WorkersConfig workersConfig;
    private final WorkersConfig httpClientWorkersConfig;
    private final ShardingConfig shardingConfig;
    private final ByteArraySerializer serializer;
    private final InconsistencyStrategyType inconsistencyStrategyType;
    private final ReadWriteLock repairLock;
    private final HashingAlgorithm hashing;

    private CustomHttpServer server;
    private DB levelDB;

    public ServiceImpl(ServiceConfig config) {
        this(config, new WorkersConfig(), new WorkersConfig(), new ShardingConfig(),
            InconsistencyStrategyType.READ_REPAIR_DIGEST);
    }

    public ServiceImpl(ServiceConfig config, WorkersConfig workersConfig,
                       WorkersConfig httpClientWorkersConfig, ShardingConfig shardingConfig,
                       InconsistencyStrategyType inconsistencyStrategyType) {
        this.config = config;
        this.workersConfig = workersConfig;
        this.httpClientWorkersConfig = httpClientWorkersConfig;
        this.shardingConfig = shardingConfig;
        this.inconsistencyStrategyType = inconsistencyStrategyType;
        serializer = ByteArraySerializerFactory.latest();
        repairLock = new ReentrantReadWriteLock();
        hashing = new HashingAlgorithm();
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        try {
            levelDB = Iq80DBFactory.factory.open(config.workingDir().toFile(), LEVELDB_OPTIONS);
        } catch (IOException e) {
            LOG.error("Error while starting database: ", e);
            throw e;
        }
        server = new CustomHttpServer(ServiceUtils.createHttpConfig(config), config, workersConfig,
            httpClientWorkersConfig, shardingConfig, inconsistencyStrategyType);
        server.setRequestHandler(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() {
        server.stop();
        try {
            levelDB.close();
        } catch (IOException e) {
            LOG.error("Error while closing: ", e);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void handleLeaderDigestGet(HandlerDigestRequest request, HandlerLeaderResponse response) {
        if (!request.getState().isSuccess()) {
            response.setResponse(new Response(NOT_ENOUGH_REPLICAS, Response.EMPTY));
            return;
        }

        LeaderRequestState leaderState = (LeaderRequestState) request.getState();
        Map<String, Response> responses = leaderState.getReplicaResponses();
        Response leaderResponse = responses.get(request.getLeaderUrl());
        byte[] leaderDigest = hashing.hash(leaderResponse.getBody());
        for (Map.Entry<String, Response> entry : responses.entrySet()) {
            if (!request.getLeaderUrl().equals(entry.getKey())
                && !Arrays.equals(entry.getValue().getBody(), leaderDigest)) {
                return;
            }
        }

        response.setEqualDigests(true);
        DBValue actualValue = serializer.deserialize(leaderResponse.getBody());
        response.setResponse(actualValue == null || actualValue.getValue() == null
            ? new Response(Response.NOT_FOUND, Response.EMPTY) : Response.ok(actualValue.getValue()));
    }

    @Override
    public void handleLeaderGet(HandlerRequest request, HandlerLeaderResponse response) {
        if (!request.getState().isSuccess()) {
            response.setResponse(new Response(NOT_ENOUGH_REPLICAS, Response.EMPTY));
            return;
        }

        LeaderRequestState leaderState = (LeaderRequestState) request.getState();
        Map<String, Response> responses = leaderState.getReplicaResponses();
        Map<String, DBValue> dbValues = new HashMap<>(responses.size());
        for (Map.Entry<String, Response> entry : responses.entrySet()) {
            byte[] body = entry.getValue().getBody();
            dbValues.put(entry.getKey(), body == null || body.length == 0 ? null : serializer.deserialize(body));
        }

        DBValue actualValue = ServiceUtils.getActualValue(dbValues);
        response.setResponse(actualValue == null || actualValue.getValue() == null
            ? new Response(Response.NOT_FOUND, Response.EMPTY) : Response.ok(actualValue.getValue()));

        if (actualValue != null
            && leaderState.getInconsistencyStrategy() instanceof RepairResolutionStrategy) {
            response.setInconsistentReplicas(ServiceUtils.getInconsistentReplicas(dbValues, actualValue));
            response.setActualValue(actualValue);
        }
    }

    @Override
    public void handleGet(HandlerRequest request, HandlerResponse response) {
        byte[] key = request.getState().getId().getBytes(StandardCharsets.UTF_8);
        byte[] value = levelDB.get(key);
        if (value == null) {
            response.setResponse(new Response(Response.NOT_FOUND, Response.EMPTY));
            return;
        }

        response.setResponse(Response.ok(request.getState().isDigestOnly() ? hashing.hash(value) : value));
    }

    @Override
    public void handleGetRange(HandlerRangeRequest request, HandlerResponse response) {
        DBIterator iterator = levelDB.iterator();
        byte[] end = request.getEnd() == null ? null : request.getEnd().getBytes(StandardCharsets.UTF_8);
        byte[] upperBound = null;
        if (end != null) {
            iterator.seek(end);
            if (iterator.hasNext()) {
                upperBound = iterator.next().getValue();
            }
        }
        ChunkedResponse chunkedResponse = new ChunkedResponse(Response.OK, Response.EMPTY);
        chunkedResponse.setUpperBound(upperBound);
        iterator.seek(request.getStart().getBytes(StandardCharsets.UTF_8));
        chunkedResponse.setIterator(iterator);
        response.setResponse(chunkedResponse);
    }

    @Override
    public void handleLeaderPut(HandlerRequest request, HandlerResponse response) {
        response.setResponse(request.getState().isSuccess()
            ? new Response(Response.CREATED, Response.EMPTY) : new Response(NOT_ENOUGH_REPLICAS, Response.EMPTY));
    }

    @Override
    public void handlePut(HandlerRequest request, HandlerResponse response) {
        byte[] key = request.getState().getId().getBytes(StandardCharsets.UTF_8);
        byte[] dbValue = serializer.serialize(
            new DBValue(request.getState().getRequest().getBody(), request.getState().getTimestamp()));
        if (inconsistencyStrategyType == InconsistencyStrategyType.NONE) {
            levelDB.put(key, dbValue);
        } else {
            Lock readLock = repairLock.readLock();
            readLock.lock();
            try {
                levelDB.put(key, dbValue);
            } finally {
                readLock.unlock();
            }
        }
        response.setResponse(new Response(Response.CREATED, Response.EMPTY));
    }

    @Override
    public void handleRepairPut(HandlerRepairRequest request) {
        if (inconsistencyStrategyType == InconsistencyStrategyType.NONE) {
            return;
        }

        byte[] key = request.getState().getId().getBytes(StandardCharsets.UTF_8);
        DBValue newValue = request.getActualValue();
        Lock writeLock = repairLock.writeLock();
        writeLock.lock();
        try {
            byte[] prevValueBytes = levelDB.get(key);
            DBValue prevValue = prevValueBytes == null ? null : serializer.deserialize(prevValueBytes);
            if (DBValue.COMPARATOR.compare(prevValue, newValue) < 0) {
                byte[] newValueBytes = serializer.serialize(newValue);
                levelDB.put(key, newValueBytes);
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void handleLeaderDelete(HandlerRequest request, HandlerResponse response) {
        response.setResponse(request.getState().isSuccess()
            ? new Response(Response.ACCEPTED, Response.EMPTY) : new Response(NOT_ENOUGH_REPLICAS, Response.EMPTY));
    }

    @Override
    public void handleDelete(HandlerRequest request, HandlerResponse response) {
        byte[] key = request.getState().getId().getBytes(StandardCharsets.UTF_8);
        levelDB.put(key, serializer.serialize(new DBValue(null, request.getState().getTimestamp())));
        response.setResponse(new Response(Response.ACCEPTED, Response.EMPTY));
    }

    @ServiceFactory(stage = 7, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
