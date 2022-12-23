package ok.dht.test.shik;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.shik.events.HandlerRequest;
import ok.dht.test.shik.events.HandlerResponse;
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
import java.util.concurrent.CompletableFuture;

public class ServiceImpl implements CustomService {

    private static final Log LOG = LogFactory.getLog(ServiceImpl.class);
    private static final Options LEVELDB_OPTIONS = new Options();

    private final ServiceConfig config;
    private final WorkersConfig workersConfig;
    private final ShardingConfig shardingConfig;

    private CustomHttpServer server;
    private DB levelDB;

    public ServiceImpl(ServiceConfig config) {
        this(config, new WorkersConfig(), new ShardingConfig());
    }

    public ServiceImpl(ServiceConfig config, WorkersConfig workersConfig, ShardingConfig shardingConfig) {
        this.config = config;
        this.workersConfig = workersConfig;
        this.shardingConfig = shardingConfig;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        try {
            levelDB = Iq80DBFactory.factory.open(config.workingDir().toFile(), LEVELDB_OPTIONS);
        } catch (IOException e) {
            LOG.error("Error while starting database: ", e);
            throw e;
        }
        server = new CustomHttpServer(createHttpConfig(config), config, workersConfig, shardingConfig);
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
    public void handleGet(HandlerRequest request, HandlerResponse response) {
        byte[] key = request.getId().getBytes(StandardCharsets.UTF_8);
        byte[] value = levelDB.get(key);
        response.setResponse(value == null ? new Response(Response.NOT_FOUND, Response.EMPTY) : Response.ok(value));
    }

    @Override
    public void handlePut(HandlerRequest request, HandlerResponse response) {
        byte[] value = request.getRequest().getBody();
        byte[] key = request.getId().getBytes(StandardCharsets.UTF_8);
        levelDB.put(key, value);
        response.setResponse(new Response(Response.CREATED, Response.EMPTY));
    }

    @Override
    public void handleDelete(HandlerRequest request, HandlerResponse response) {
        byte[] key = request.getId().getBytes(StandardCharsets.UTF_8);
        levelDB.delete(key);
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

    @ServiceFactory(stage = 3, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
