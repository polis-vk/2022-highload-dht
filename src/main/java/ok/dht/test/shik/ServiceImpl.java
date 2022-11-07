package ok.dht.test.shik;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.shik.events.HandlerRequest;
import ok.dht.test.shik.events.HandlerResponse;
import ok.dht.test.shik.events.LeaderRequestState;
import ok.dht.test.shik.model.DBValue;
import ok.dht.test.shik.serialization.ByteArraySerializer;
import ok.dht.test.shik.serialization.ByteArraySerializerFactory;
import ok.dht.test.shik.sharding.ShardingConfig;
import ok.dht.test.shik.workers.WorkersConfig;
import one.nio.http.HttpServerConfig;
import one.nio.http.Response;
import one.nio.net.ConnectionString;
import one.nio.server.ServerConfig;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.Iq80DBFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ServiceImpl implements CustomService {

    private static final Log LOG = LogFactory.getLog(ServiceImpl.class);
    private static final Options LEVELDB_OPTIONS = new Options();
    private static final String NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";

    private final ServiceConfig config;
    private final WorkersConfig workersConfig;
    private final WorkersConfig httpClientWorkersConfig;
    private final ShardingConfig shardingConfig;
    private final ByteArraySerializer serializer;

    private CustomHttpServer server;
    private DB levelDB;

    public ServiceImpl(ServiceConfig config) {
        this(config, new WorkersConfig(), new WorkersConfig(), new ShardingConfig());
    }

    public ServiceImpl(ServiceConfig config, WorkersConfig workersConfig,
                       WorkersConfig httpClientWorkersConfig, ShardingConfig shardingConfig) {
        this.config = config;
        this.workersConfig = workersConfig;
        this.httpClientWorkersConfig = httpClientWorkersConfig;
        this.shardingConfig = shardingConfig;
        serializer = ByteArraySerializerFactory.latest();
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        try {
            levelDB = Iq80DBFactory.factory.open(config.workingDir().toFile(), LEVELDB_OPTIONS);
        } catch (IOException e) {
            LOG.error("Error while starting database: ", e);
            throw e;
        }
        server = new CustomHttpServer(createHttpConfig(config), config, workersConfig, httpClientWorkersConfig, shardingConfig);
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
    public void handleLeaderGet(HandlerRequest request, HandlerResponse response) {
        if (!request.getState().isSuccess()) {
            response.setResponse(new Response(NOT_ENOUGH_REPLICAS, Response.EMPTY));
            return;
        }

        Optional<byte[]> latestValue = ((LeaderRequestState) request.getState()).getShardResponses().stream()
            .filter(resp -> resp.getBody().length != 0)
            .map(resp -> ByteArraySerializerFactory.latest().deserialize(resp.getBody()))
            .max(DBValue.COMPARATOR)
            .map(DBValue::getValue);
        response.setResponse(latestValue.map(Response::ok)
            .orElseGet(() -> new Response(Response.NOT_FOUND, Response.EMPTY)));
    }

    @Override
    public void handleGet(HandlerRequest request, HandlerResponse response) {
        byte[] key = request.getState().getId().getBytes(StandardCharsets.UTF_8);
        byte[] value = levelDB.get(key);
        if (value == null) {
            response.setResponse(new Response(Response.NOT_FOUND, Response.EMPTY));
            return;
        }

        response.setResponse(Response.ok(value));
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
        levelDB.put(key, dbValue);
        response.setResponse(new Response(Response.CREATED, Response.EMPTY));
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

    private static HttpServerConfig createHttpConfig(ServiceConfig config) {
        ServerConfig serverConfig = ServerConfig.from(new ConnectionString(config.selfUrl()));
        HttpServerConfig httpServerConfig = new HttpServerConfig();
        httpServerConfig.acceptors = serverConfig.acceptors;
        Arrays.stream(httpServerConfig.acceptors).forEach(x -> x.reusePort = true);
        httpServerConfig.schedulingPolicy = serverConfig.schedulingPolicy;
        return httpServerConfig;
    }

    @ServiceFactory(stage = 5, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
