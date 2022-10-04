package ok.dht.test.ushkov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import one.nio.util.Utf8;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class RocksDBService implements Service {
    public static final int N_SELECTORS = 5;
    public static final int N_WORKERS = 5;
    public static final int QUEUE_CAP = 100;

    public static final long STOP_TIMEOUT_MINUTES = 1;

    private final ServiceConfig config;
    public RocksDB db;
    private RocksDBHttpServer server;

    public RocksDBService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        try {
            db = RocksDB.open(config.workingDir().toString());
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
        server = new RocksDBHttpServer(createHttpServerConfigFromPort(config.selfPort()));
        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    private static RocksDBHttpServerConfig createHttpServerConfigFromPort(int port) {
        RocksDBHttpServerConfig httpConfig = new RocksDBHttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        httpConfig.selectors = N_SELECTORS;
        httpConfig.workers = N_WORKERS;
        httpConfig.queueCapacity = QUEUE_CAP;
        return httpConfig;
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            server.stop();
            try {
                server.awaitStop(STOP_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            server = null;
        });

        try {
            db.closeE();
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
        db = null;

        return future;
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response entityGet(@Param(value = "id", required = true) String id) {
        if (id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        try {
            byte[] value = db.get(Utf8.toBytes(id));
            if (value == null) {
                return new Response(Response.NOT_FOUND, Response.EMPTY);
            } else {
                return new Response(Response.OK, value);
            }
        } catch (RocksDBException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response entityPut(@Param(value = "id", required = true) String id, Request request) {
        if (id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        try {
            db.put(Utf8.toBytes(id), request.getBody());
            return new Response(Response.CREATED, Response.EMPTY);
        } catch (RocksDBException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response entityDelete(@Param(value = "id", required = true) String id) {
        if (id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        try {
            db.delete(Utf8.toBytes(id));
            return new Response(Response.ACCEPTED, Response.EMPTY);
        } catch (RocksDBException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    @ServiceFactory(stage = 1, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new RocksDBService(config);
        }
    }
}
