package ok.dht.test.monakhov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.monakhov.hashing.JumpingNodesRouter;
import ok.dht.test.monakhov.hashing.NodesRouter;
import one.nio.http.HttpException;
import one.nio.http.HttpServer;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.ConnectionString;
import one.nio.pool.PoolException;
import one.nio.server.AcceptorConfig;
import one.nio.util.Utf8;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DbService implements Service {
    private static final Logger log = LoggerFactory.getLogger(DbService.class);
    private static final int QUEUE_SIZE = 1000;
    private final ServiceConfig serviceConfig;
    private final NodesRouter nodesRouter;
    private ScheduledThreadPoolExecutor monitoringExecutor;
    private HashMap<String, AsyncHttpClient> nodeClients;
    private RocksDB dao;
    private HttpServer server;

    public DbService(ServiceConfig serviceConfig) {
        this.serviceConfig = serviceConfig;
        nodesRouter = new JumpingNodesRouter(serviceConfig.clusterUrls());
    }

    private AsyncHttpServerConfig createConfigFromPort(int port) {
        AsyncHttpServerConfig httpConfig = new AsyncHttpServerConfig();
        httpConfig.clusterUrls = serviceConfig.clusterUrls();
        httpConfig.selfUrl = serviceConfig.selfUrl();
        httpConfig.workersNumber = Runtime.getRuntime().availableProcessors() / 2;
        httpConfig.queueSize = QUEUE_SIZE;
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[] {acceptor};
        return httpConfig;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        server = new AsyncHttpServer(createConfigFromPort(serviceConfig.selfPort()));
        try {
            dao = RocksDB.open(serviceConfig.workingDir().toString());
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
        nodeClients = new HashMap<>();
        for (String url: serviceConfig.clusterUrls()) {
            nodeClients.put(url, new AsyncHttpClient(new ConnectionString(url)));
        }

        monitoringExecutor = new ScheduledThreadPoolExecutor(
            Math.max(1, serviceConfig.clusterUrls().size() / 10)
        );

        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        if (server == null || dao == null || nodeClients == null || monitoringExecutor == null) {
            return CompletableFuture.completedFuture(null);
        }
        server.stop();
        try {
            dao.syncWal();
            dao.closeE();
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
        for (var entry : nodeClients.entrySet()) {
            entry.getValue().close();
        }
        ExecutorUtils.shutdownGracefully(monitoringExecutor, log);
        server = null;
        dao = null;
        nodeClients = null;
        monitoringExecutor = null;
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    public Response manageRequest(@Param(value = "id") String id, Request request) {
        if (id == null || id.isBlank()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }

        String nodeUrl = nodesRouter.getNodeUrl(id);
        if (nodeUrl.equals(serviceConfig.selfUrl())) {
            try {
                return switch (request.getMethod()) {
                    case Request.METHOD_GET -> getEntity(id);
                    case Request.METHOD_PUT -> putEntity(id, request);
                    case Request.METHOD_DELETE -> deleteEntity(id);
                    default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
                };
            } catch (RocksDBException e) {
                return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
            }
        } else {
            final var client = nodeClients.get(nodeUrl);
            try {
                // Клиент может быть недоступен после проверки условия, но пиво мы уже открыли,
                // да и это ничего не сломает из-за cas
                if (client.available().get()) {
                    return client.invoke(request);
                }
                return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
            } catch (PoolException e) {
                log.debug("Node: {} is unavailable", nodeUrl, e);
                if (client.available().compareAndSet(true, false)) {
                    Runnable monitoringTask = new Runnable() {
                        @Override
                        public void run() {
                            try {
                                client.connect(nodeUrl);
                                client.available().compareAndSet(false, true);
                            } catch (InterruptedException | PoolException | IOException | HttpException ex) {
                                log.debug("Impossible to establish connection to node: {}", nodeUrl, ex);
                                monitoringExecutor.schedule(this, 5L, TimeUnit.SECONDS);
                            }
                        }
                    };
                    monitoringExecutor.schedule(monitoringTask, 0L, TimeUnit.SECONDS);
                }
                return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
            } catch (IOException | HttpException | InterruptedException e) {
                log.error("Exception occurred while redirecting request to another node", e);
                return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
            }
        }
    }

    public Response getEntity(String id) throws RocksDBException {
        final var entry = dao.get(Utf8.toBytes(id));

        if (entry == null) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }

        return new Response(Response.OK, entry);
    }

    public Response putEntity(String id, Request request) throws RocksDBException {
        dao.put(Utf8.toBytes(id), request.getBody());
        return new Response(Response.CREATED, Response.EMPTY);
    }

    public Response deleteEntity(String id) throws RocksDBException {
        dao.delete(Utf8.toBytes(id));
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    @ServiceFactory(stage = 3, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new DbService(config);
        }
    }
}
