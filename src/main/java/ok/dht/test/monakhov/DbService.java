package ok.dht.test.monakhov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import one.nio.http.HttpServer;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import one.nio.util.Utf8;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class DbService implements Service {
    private static final int QUEUE_SIZE = 1000;
    private final ServiceConfig serviceConfig;
    private RocksDB dao;
    private HttpServer server;

    public DbService(ServiceConfig serviceConfig) {
        this.serviceConfig = serviceConfig;
    }

    private static AsyncHttpServerConfig createConfigFromPort(int port) {
        AsyncHttpServerConfig httpConfig = new AsyncHttpServerConfig(
            Runtime.getRuntime().availableProcessors(),
            QUEUE_SIZE
        );
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
        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        try {
            dao.syncWal();
            dao.closeE();
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    public Response manageRequest(@Param(value = "id") String id, Request request) {
        if (id == null || id.isBlank()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        try {
            return switch (request.getMethod()) {
                case Request.METHOD_GET -> getEntity(id);
                case Request.METHOD_PUT -> putEntity(id, request);
                case Request.METHOD_DELETE -> deleteEntity(id);
                default -> new Response(Response.METHOD_NOT_ALLOWED);
            };
        } catch (RocksDBException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
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

    @ServiceFactory(stage = 2, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new DbService(config);
        }
    }
}
