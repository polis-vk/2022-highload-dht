package ok.dht.test.ushkov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
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

public class RocksDBService implements Service {
    private final ServiceConfig config;
    private RocksDB db;
    private HttpServer server;

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
        httpConfig.selectors = 5;
        httpConfig.workers = 5;
        httpConfig.queueCapacity = 100;
        return httpConfig;
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        server = null;

        try {
            db.closeE();
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
        db = null;

        return CompletableFuture.completedFuture(null);
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

    @ServiceFactory(stage = 2, week = 1)
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new RocksDBService(config);
        }
    }
}
