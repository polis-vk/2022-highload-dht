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
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class TycoonService implements ok.dht.Service {
    private static final Logger LOG = LoggerFactory.getLogger(TycoonService.class);
    private static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();
    public static final String PATH = "/v0/entity";
    public static final int THREAD_POOL_QUEUE_CAPACITY = 128;
    private final ServiceConfig config;
    private final NodeMapper nodeMapper;
    private DB levelDb;
    private TycoonHttpServer server;
    private ExecutorService executorService;
    private HttpClient httpClient;

    public TycoonService(ServiceConfig config) {
        this.config = config;
        this.nodeMapper = new NodeMapper(config.clusterUrls());
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

        switch (request.getMethod()) {
            case Request.METHOD_GET -> execute(session, () -> get(idParameter, request));
            case Request.METHOD_PUT -> execute(session, () -> upsert(idParameter, request));
            case Request.METHOD_DELETE -> execute(session, () -> delete(idParameter, request));
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

    private Response get(String id, Request request) {
        byte[] value = null;

        String nodeUrlByKey = nodeMapper.getNodeUrlByKey(Utf8.toBytes(id));
        if (config.selfUrl().equals(nodeUrlByKey)) {
            try {
                value = levelDb.get(Utf8.toBytes(id));
            } catch (DBException e) {
                LOG.error("Error in DB", e);
                return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
            }
        } else {
            try {
                HttpResponse<byte[]> response = httpClient.send(
                    HttpRequest.newBuilder()
                        .GET()
                        .uri(URI.create(nodeUrlByKey + PATH + "?id=" + id))
                        .build(),
                    HttpResponse.BodyHandlers.ofByteArray()
                );
                if (response.statusCode() == HttpURLConnection.HTTP_OK) {
                    value = response.body();
                } else if (response.statusCode() != HttpURLConnection.HTTP_NOT_FOUND) {
                    LOG.error("Unexpected status {} code requesting node {}", response.statusCode(), nodeUrlByKey);
                    return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
                }
            } catch (IOException | InterruptedException e) {
                LOG.error("Error while requesting node {}", nodeUrlByKey, e);
                return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
            }
        }

        if (value == null) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        } else {
            return new Response(Response.OK, value);
        }
    }

    private Response upsert(String id, Request request) {
        byte[] value = Response.EMPTY;

        String nodeUrlByKey = nodeMapper.getNodeUrlByKey(Utf8.toBytes(id));
        if (config.selfUrl().equals(nodeUrlByKey)) {
            try {
                levelDb.put(Utf8.toBytes(id), request.getBody());
            } catch (DBException e) {
                LOG.error("Error in DB", e);
                return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
            }
        } else {
            try {
                HttpResponse<byte[]> response = httpClient.send(
                    HttpRequest.newBuilder()
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(request.getBody()))
                        .uri(URI.create(nodeUrlByKey + PATH + "?id=" + id))
                        .build(),
                    HttpResponse.BodyHandlers.ofByteArray()
                );
                if (response.statusCode() == HttpURLConnection.HTTP_CREATED) {
                    value = response.body();
                } else {
                    LOG.error("Unexpected status {} code requesting node {}", response.statusCode(), nodeUrlByKey);
                    return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
                }
            } catch (IOException | InterruptedException e) {
                LOG.error("Error while requesting node {}", nodeUrlByKey, e);
                return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
            }
        }

        return new Response(Response.CREATED, value);
    }

    private Response delete(String id, Request request) {
        byte[] value = Response.EMPTY;

        String nodeUrlByKey = nodeMapper.getNodeUrlByKey(Utf8.toBytes(id));
        if (config.selfUrl().equals(nodeUrlByKey)) {
            try {
                levelDb.delete(Utf8.toBytes(id));
            } catch (DBException e) {
                LOG.error("Error in DB", e);
                return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
            }
        } else {
            try {
                HttpResponse<byte[]> response = httpClient.send(
                    HttpRequest.newBuilder()
                        .DELETE()
                        .uri(URI.create(nodeUrlByKey + PATH + "?id=" + id))
                        .build(),
                    HttpResponse.BodyHandlers.ofByteArray()
                );
                if (response.statusCode() == HttpURLConnection.HTTP_ACCEPTED) {
                    value = response.body();
                } else {
                    LOG.error("Unexpected status {} code requesting node {}", response.statusCode(), nodeUrlByKey);
                    return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
                }
            } catch (IOException | InterruptedException e) {
                LOG.error("Error while requesting node {}", nodeUrlByKey, e);
                return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
            }
        }

        return new Response(Response.ACCEPTED, value);
    }

    @ServiceFactory(stage = 3, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public ok.dht.Service create(ServiceConfig config) {
            return new TycoonService(config);
        }
    }
}
