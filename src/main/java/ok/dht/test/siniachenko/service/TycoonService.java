package ok.dht.test.siniachenko.service;

import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.siniachenko.TycoonHttpServer;
import ok.dht.test.siniachenko.rendezvoushashing.NodeMapper;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TycoonService implements ok.dht.Service {
    private static final Logger LOG = LoggerFactory.getLogger(TycoonService.class);
    private static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();
    public static final String PATH = "/v0/entity";
    public static final int THREAD_POOL_QUEUE_CAPACITY = 128;
    public static final int DEFAULT_TIMEOUT_MILLIS = 1000;
    public static final int MIN_TIMEOUT_MILLIS = 300;
    public static final int MAX_TIMEOUT_MILLIS = 2000;
    private final ServiceConfig config;
    private final NodeMapper nodeMapper;
    private DB levelDb;
    private TycoonHttpServer server;
    private ExecutorService executorService;
    private HttpClient httpClient;
    private ConcurrentMap<String, Integer> nodeRequestsTimeouts;

    public TycoonService(ServiceConfig config) {
        this.config = config;
        this.nodeMapper = new NodeMapper(config.clusterUrls());
        this.nodeRequestsTimeouts = new ConcurrentHashMap<>();
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        // DB
        levelDb = new DbImpl(new Options(), config.workingDir().toFile());
        LOG.info("Started DB in directory {}", config.workingDir());

        // Executor
        int threadPoolSize = AVAILABLE_PROCESSORS;
        executorService = new ThreadPoolExecutor(
            threadPoolSize, threadPoolSize,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(THREAD_POOL_QUEUE_CAPACITY)
        );

        // Http Client
        httpClient = HttpClient.newHttpClient();

        // Http Server
        server = new TycoonHttpServer(this, config.selfPort());
        server.start();
        LOG.info("Service started on {}, executor threads: {}", config.selfUrl(), threadPoolSize);

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        if (server != null && !server.isClosed()) {
            server.stop();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
        if (levelDb != null) {
            levelDb.close();
        }

        return CompletableFuture.completedFuture(null);
    }

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

        String nodeUrlByKey = nodeMapper.getNodeUrlByKey(Utf8.toBytes(idParameter));
        if (config.selfUrl().equals(nodeUrlByKey)) {
            executeLocal(session, request, idParameter);
        } else {
            proxyRequest(session, request, idParameter, nodeUrlByKey);
        }
    }

    private void proxyRequest(HttpSession session, Request request, String idParameter, String nodeUrl) {
        Integer timeout = nodeRequestsTimeouts.computeIfAbsent(nodeUrl, s -> DEFAULT_TIMEOUT_MILLIS);
        httpClient.sendAsync(
            HttpRequest.newBuilder()
                .uri(URI.create(nodeUrl + PATH + "?id=" + idParameter))
                .method(request.getMethodName(), HttpRequest.BodyPublishers.ofByteArray(request.getBody()))
                .build(),
            HttpResponse.BodyHandlers.ofByteArray()
        ).thenAccept(response -> {
            String statusCode;
            if (response.statusCode() == 404) {
                statusCode = Response.NOT_FOUND;
            } else if (response.statusCode() == 200) {
                statusCode = Response.OK;
            } else if (response.statusCode() == 201) {
                statusCode = Response.CREATED;
            } else if (response.statusCode() == 202) {
                statusCode = Response.ACCEPTED;
            } else {
                LOG.error("Unexpected status {} code requesting node {}", response.statusCode(), nodeUrl);
                statusCode = Response.INTERNAL_ERROR;
            }
            Response proxyResponse = new Response(statusCode, response.body());
            sendResponse(session, proxyResponse);
            nodeRequestsTimeouts.compute(nodeUrl, (url, t) -> Math.max(t * 2, MIN_TIMEOUT_MILLIS));
        }).orTimeout(timeout, TimeUnit.MILLISECONDS)
        .exceptionally(ex -> {
            nodeRequestsTimeouts.compute(nodeUrl, (url, t) -> Math.min(t / 2, MAX_TIMEOUT_MILLIS));
            LOG.error("Error after proxy request to {}", nodeUrl, ex);
            Response proxyResponse = new Response(Response.INTERNAL_ERROR, Response.EMPTY);
            sendResponse(session, proxyResponse);
            return null;
        });
    }

    private void executeLocal(HttpSession session, Request request, String id) {
        try {
            executorService.execute(() -> {
                Response response = switch (request.getMethod()) {
                    case Request.METHOD_GET -> get(id);
                    case Request.METHOD_PUT -> upsert(id, request.getBody());
                    case Request.METHOD_DELETE -> delete(id);
                    default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
                };
                sendResponse(session, response);
            });
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

    private Response get(String id) {
        try {
            byte[] value = levelDb.get(Utf8.toBytes(id));
            if (value == null) {
                return new Response(Response.NOT_FOUND, Response.EMPTY);
            } else {
                return new Response(Response.OK, value);
            }
        } catch (DBException e) {
            LOG.error("Error in DB", e);
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    private Response upsert(String id, byte[] value) {
        try {
            levelDb.put(Utf8.toBytes(id), value);
            return new Response(Response.CREATED, Response.EMPTY);
        } catch (DBException e) {
            LOG.error("Error in DB", e);
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    private Response delete(String id) {
        try {
            levelDb.delete(Utf8.toBytes(id));
            return new Response(Response.ACCEPTED, Response.EMPTY);
        } catch (DBException e) {
            LOG.error("Error in DB", e);
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    @ServiceFactory(stage = 3, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public ok.dht.Service create(ServiceConfig config) {
            return new TycoonService(config);
        }
    }
}
