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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

public class DhtService implements Service {

    private static final Logger LOGGER = LoggerFactory.getLogger(DhtServer.class);

    private final ServiceConfig config;
    private final ExecutorService executorService = new ForkJoinPool();
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
                if (request.getMethod() == Request.METHOD_POST) {
                    session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
                } else {
                    session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                }
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
    public void handleGet(@Param(value = "id", required = true) String id, HttpSession session) {
        submit(session, () -> {
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
        });
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public void handlePut(Request request, @Param(value = "id", required = true) String id, HttpSession session) {
        submit(session, () -> {
            if (!validateId(id)) {
                return new Response(Response.BAD_REQUEST, Response.EMPTY);
            }
            try {
                Db.put(id, request.getBody());
            } catch (DbException e) {
                return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
            }
            return new Response(Response.CREATED, Response.EMPTY);
        });
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public void handleDelete(@Param(value = "id", required = true) String id, HttpSession session) {
        submit(session, () -> {
            if (!validateId(id)) {
                return new Response(Response.BAD_REQUEST, Response.EMPTY);
            }
            try {
                Db.delete(id);
            } catch (DbException e) {
                return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
            }
            return new Response(Response.ACCEPTED, Response.EMPTY);
        });
    }

    private void submit(HttpSession session, Supplier<Response> handel) {
        executorService.execute(() -> {
            try {
                session.sendResponse(handel.get());
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
                session.close();
            }
        });
    }

    @ServiceFactory(stage = 2, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new DhtService(config);
        }
    }
}
