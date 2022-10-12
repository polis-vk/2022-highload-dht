package ok.dht.test.siniachenko.service;

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
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import one.nio.util.Utf8;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.DbImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class Service implements ok.dht.Service {
    private static final Logger LOG = LoggerFactory.getLogger(Service.class);
    private static final String PATH = "/v0/entity";
    private static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();
    private static final int THREAD_POOL_QUEUE_CAPACITY = 1024 * 1024;

    private final ServiceConfig config;
    private DB levelDb;
    private HttpServer server;
    private ExecutorService executorService;

    public Service(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        levelDb = new DbImpl(new Options(), config.workingDir().toFile());
        LOG.info("Started DB in directory {}", config.workingDir());

        server = new HttpServer(createConfigFromPort(config.selfPort())) {
            @Override
            public void handleRequest(Request request, HttpSession session) throws IOException {
                if (!PATH.equals(request.getPath())) {
                    session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                    return;
                }

                String idParameter = request.getParameter("id=");
                if (idParameter == null || idParameter.isEmpty()) {
                    session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                    return;
                }

                switch (request.getMethod()) {
                    case Request.METHOD_GET -> execute(session, () -> getEntity(idParameter));
                    case Request.METHOD_PUT -> execute(session, () -> upsertEntity(idParameter, request));
                    case Request.METHOD_DELETE -> execute(session, () -> deleteEntity(idParameter));
                    default -> execute(session, () -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
                }
            }

            @Override
            public synchronized void stop() {
                for (SelectorThread selectorThread : selectors) {
                    for (Session session : selectorThread.selector) {
                        session.close();
                    }
                }
                super.stop();
            }
        };

        executorService = new ThreadPoolExecutor(
            AVAILABLE_PROCESSORS, AVAILABLE_PROCESSORS,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(THREAD_POOL_QUEUE_CAPACITY)
        );
        server.addRequestHandlers(this);
        server.start();
        LOG.info("Service started on {}, executor threads: {}", config.selfUrl(), AVAILABLE_PROCESSORS);
        return CompletableFuture.completedFuture(null);
    }

    private void execute(HttpSession session, Supplier<Response> supplier) {
        try {
            executorService.execute(() -> sendResponse(session, supplier.get()));
        } catch (RejectedExecutionException e) {
            LOG.error("Cannot execute task", e);
            sendResponse(session, new Response(Response.INTERNAL_ERROR, Response.EMPTY));
        }
    }

    private void sendResponse(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e1) {
            LOG.error("I/O error while sending response", e1);
            try {
                session.close();
            } catch (Exception e2) {
                e2.addSuppressed(e1);
                LOG.error("Exception while closing session", e2);
            }
        }
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpServerConfig = new HttpServerConfig();
        AcceptorConfig acceptorConfig = new AcceptorConfig();
        acceptorConfig.port = port;
        acceptorConfig.reusePort = true;
        httpServerConfig.acceptors = new AcceptorConfig[]{acceptorConfig};
        httpServerConfig.selectors = AVAILABLE_PROCESSORS;
        return httpServerConfig;
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        levelDb.close();
        executorService.shutdown();
        return CompletableFuture.completedFuture(null);
    }

    @Path(PATH)
    @RequestMethod(Request.METHOD_GET)
    public Response getEntity(@Param(value = "id", required = true) String id) {
        if (id == null || id.isEmpty()) {
            return new Response(
                Response.BAD_REQUEST,
                Response.EMPTY
            );
        }
        byte[] value = levelDb.get(Utf8.toBytes(id));
        if (value == null) {
            return new Response(
                Response.NOT_FOUND,
                Response.EMPTY
            );
        } else {
            return new Response(
                Response.OK,
                value
            );
        }
    }

    @Path(PATH)
    @RequestMethod(Request.METHOD_PUT)
    public Response upsertEntity(
        @Param(value = "id", required = true) String id,
        Request request
    ) {
        if (id == null || id.isEmpty()) {
            return new Response(
                Response.BAD_REQUEST,
                Response.EMPTY
            );
        }
        levelDb.put(Utf8.toBytes(id), request.getBody());
        return new Response(
            Response.CREATED,
            Response.EMPTY
        );
    }

    @Path(PATH)
    @RequestMethod(Request.METHOD_DELETE)
    public Response deleteEntity(
        @Param(value = "id", required = true) String id
    ) {
        if (id == null || id.isEmpty()) {
            return new Response(
                Response.BAD_REQUEST,
                Response.EMPTY
            );
        }
        levelDb.delete(Utf8.toBytes(id));
        return new Response(
            Response.ACCEPTED,
            Response.EMPTY
        );
    }

    @ServiceFactory(stage = 2, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public ok.dht.Service create(ServiceConfig config) {
            return new Service(config);
        }
    }
}
