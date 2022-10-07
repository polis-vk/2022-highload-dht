package ok.dht.test.siniachenko.service;

import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.siniachenko.TycoonHttpServer;
import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.util.Utf8;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.DbImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class TycoonService implements ok.dht.Service {
    private static final Logger LOG = LoggerFactory.getLogger(TycoonService.class);
    private static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();
    public static final String REQUESTS_PATH = "/v0/entity";
    public static final int THREAD_POOL_QUEUE_CAPACITY = (int) 1E20;

    private final ServiceConfig config;
    private DB levelDb;
    private HttpServer server;
    private ExecutorService executorService;

    public TycoonService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        levelDb = new DbImpl(new Options(), config.workingDir().toFile());
        LOG.info("Started DB in directory {}", config.workingDir());

        int threadPoolSize = AVAILABLE_PROCESSORS;
        executorService = new ThreadPoolExecutor(
            threadPoolSize, threadPoolSize,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(THREAD_POOL_QUEUE_CAPACITY)
        );

        server = new TycoonHttpServer(this, config.selfPort());
        server.start();
        LOG.info("Service started on {}, executor threads: {}", config.selfUrl(), threadPoolSize);

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        levelDb.close();
        executorService.shutdown();
        return CompletableFuture.completedFuture(null);
    }

    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (!REQUESTS_PATH.equals(request.getPath())) {
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

    private Response getEntity(String id) {
        byte[] value;
        try {
            value = levelDb.get(Utf8.toBytes(id));
        } catch (DBException e) {
            LOG.error("Error in DB");
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }

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

    private Response upsertEntity(String id, Request request) {
        try {
            levelDb.put(Utf8.toBytes(id), request.getBody());
        } catch (DBException e) {
            LOG.error("Error in DB");
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }

        return new Response(
            Response.CREATED,
            Response.EMPTY
        );
    }

    private Response deleteEntity(String id) {
        try {
            levelDb.delete(Utf8.toBytes(id));
        } catch (DBException e) {
            LOG.error("Error in DB");
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }

        return new Response(
            Response.ACCEPTED,
            Response.EMPTY
        );
    }

    @ServiceFactory(stage = 2, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public ok.dht.Service create(ServiceConfig config) {
            return new TycoonService(config);
        }
    }
}
