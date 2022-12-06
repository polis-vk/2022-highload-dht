package ok.dht.test.shashulovskiy;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import one.nio.util.Utf8;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.iq80.leveldb.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.iq80.leveldb.impl.Iq80DBFactory.factory;

public class ServiceImpl implements Service {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceImpl.class);
    private static final int MAXIMUM_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = Math.max(1, MAXIMUM_POOL_SIZE / 4);

    private final ServiceConfig config;
    private HttpServer server;

    private DB dao;

    private ExecutorService requestHandlerPool;

    public ServiceImpl(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        Options options = new Options();
        options.createIfMissing(true);

        this.dao = factory.open(config.workingDir().toFile(), options);

        this.requestHandlerPool = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAXIMUM_POOL_SIZE,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingStack<>()
        );

        // TODO Extract this after Stage 1 is merged
        server = new HttpServer(createConfigFromPort(config.selfPort())) {

            @Override
            public void handleRequest(Request request, HttpSession session) throws IOException {
                try {
                    requestHandlerPool.submit(() -> {
                        try {
                            super.handleRequest(request, session);
                        } catch (IOException e) {
                            LOG.error("IO Exception occurred while processing request: " + e.getMessage(), e);
                        }
                    });
                } catch (RejectedExecutionException e) {
                    LOG.warn("Request rejected", e);
                    session.sendResponse(new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY));
                }
            }

            @Override
            public void handleDefault(Request request, HttpSession session) throws IOException {
                Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
                session.sendResponse(response);
            }

            @Override
            public synchronized void stop() {
                for (SelectorThread selector : selectors) {
                    selector.selector.forEach(Session::close);
                }

                super.stop();
            }
        };
        server.start();
        server.addRequestHandlers(this);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() {
        server.stop();
        Utils.shutdownAndAwaitTermination(requestHandlerPool);

        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    public Response handle(Request request) {
        String id = request.getParameter("id=");
        if (id == null) {
            return new Response(
                    Response.BAD_REQUEST,
                    Utf8.toBytes("No id provided")
            );
        } else if (id.isEmpty()) {
            return new Response(
                    Response.BAD_REQUEST,
                    Utf8.toBytes("Empty id")
            );
        }

        try {
            switch (request.getMethod()) {
                case Request.METHOD_GET -> {
                    byte[] result = dao.get(Utf8.toBytes(id));
                    if (result == null) {
                        return new Response(Response.NOT_FOUND, Response.EMPTY);
                    } else {
                        return new Response(Response.OK, result);
                    }
                }
                case Request.METHOD_PUT -> {
                    dao.put(Utf8.toBytes(id), request.getBody());

                    return new Response(Response.CREATED, Response.EMPTY);
                }
                case Request.METHOD_DELETE -> {
                    dao.delete(Utf8.toBytes(id));

                    return new Response(Response.ACCEPTED, Response.EMPTY);
                }
                default -> {
                    return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
                }
            }
        } catch (DBException exception) {
            LOG.error("Internal dao exception occurred on " + request.getPath(), exception);
            return new Response(
                    Response.INTERNAL_ERROR,
                    Utf8.toBytes("An error occurred when accessing database.")
            );
        }
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @ServiceFactory(stage = 2, week = 1, bonuses = {"SingleNodeTest#respectFileFolder"})
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
