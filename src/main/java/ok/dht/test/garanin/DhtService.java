package ok.dht.test.garanin;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.garanin.db.Db;
import ok.dht.test.garanin.db.DbException;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class DhtService implements Service {

    private final ServiceConfig config;
    private HttpServer server;

    public DhtService(ServiceConfig config) {
        this.config = config;
    }

    private static boolean validateId(String id) {
        return !id.isEmpty();
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        Db.open(config.workingDir());
        server = new HttpServer(createConfigFromPort(config.selfPort())) {
            @Override
            public void handleDefault(Request request, HttpSession session) throws IOException {
                session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            }

            @Override
            public synchronized void stop() {
                for (var selectorThread : selectors) {
                    for (var session : selectorThread.selector) {
                        session.close();
                    }
                }
                super.stop();
            }
        };
        server.start();
        server.addRequestHandlers(this);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        Db.close();
        server.stop();
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response handleGet(@Param(value = "id", required = true) String id) {
        if (!validateId(id)) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        byte[] value;
        try {
            value = Db.get(id);
        } catch (DbException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
        if (value == null) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }
        return new Response(Response.OK, value);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(Request request, @Param(value = "id", required = true) String id) {
        if (!validateId(id)) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        try {
            Db.put(id, request.getBody());
        } catch (DbException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
        return new Response(Response.CREATED, Response.EMPTY);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(@Param(value = "id", required = true) String id) {
        if (!validateId(id)) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        try {
            Db.delete(id);
        } catch (DbException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    @ServiceFactory(stage = 1, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new DhtService(config);
        }
    }
}
