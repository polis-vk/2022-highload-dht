package ok.dht.test.maximenko;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.maximenko.dao.BaseEntry;
import ok.dht.test.maximenko.dao.Config;
import ok.dht.test.maximenko.dao.Dao;
import ok.dht.test.maximenko.dao.Entry;
import ok.dht.test.maximenko.dao.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class DatabaseHttpServer extends HttpServer {
    static final int flushDaoThresholdBytes = 10000000;
    private final static String requestPath = "/v0/entity";
    static final private Response badRequest = new Response(
            String.valueOf(HttpURLConnection.HTTP_BAD_REQUEST),
            Response.EMPTY
    );
    private Dao dao;

    private final KeyDispatcher keyDispatcher;
    private final HttpClient httpClient;
    private ExecutorService requestHandlers;
    private final String[] clusterConfig;
    private final java.nio.file.Path workDir;
    private final int selfId;
    public DatabaseHttpServer(HttpServerConfig httpConfig, ServiceConfig config) throws IOException {
        super(httpConfig);
        this.workDir = config.workingDir();
        List<Integer> nodesIds = IntStream.range(0, config.clusterUrls().size())
                                        .boxed()
                .                       collect(Collectors.toList());
        keyDispatcher = new KeyDispatcher(nodesIds);
        this.clusterConfig = config.clusterUrls().toArray(new String[0]);
        selfId = config.clusterUrls().indexOf(config.selfUrl());
        httpClient = HttpClient.newHttpClient();
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (!request.getPath().equals(requestPath)) {
            session.sendResponse(badRequest);
            return;
        }

        String key = request.getParameter("id=");
        if (key == null || key.equals("")) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        int targetNodeId = keyDispatcher.getNode(key);
        if (targetNodeId != selfId) {
            requestHandlers.execute(() -> {
                Response response = proxyRequest(targetNodeId, key, request);
                try {
                    session.sendResponse(response);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            return;
        }

        requestHandlers.execute(() -> {
            try {
                Response response = handleMethod(key, request.getMethod(), request.getBody());
                session.sendResponse(response);
            } catch (IOException e) {
                try {
                    session.sendResponse(new Response(Response.INTERNAL_ERROR, Response.EMPTY));
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    private Response proxyRequest(int targetNodeId, String key, Request request) {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(clusterConfig[targetNodeId] + requestPath + "?id=" + key))
                .timeout(Duration.ofSeconds(1))
                .method(request.getMethodName(), HttpRequest.BodyPublishers.ofByteArray(request.getBody()))
                .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            return new Response(convertStatusCode(response.statusCode()), response.body());
        } catch (Exception e) {
            return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
        }
    }

    Response handleMethod(String key, int method, byte[] body) throws IOException {
        switch (method) {
            case Request.METHOD_GET:
                return handleGet(key);
            case Request.METHOD_DELETE:
                return handleDelete(key);
            case Request.METHOD_PUT:
                return handlePut(key, body);
            default:
                return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
        }
    }

    private Response handlePut(String keyString, byte[] body) {
        MemorySegment key = MemorySegment.ofArray(keyString.getBytes(StandardCharsets.UTF_8));
        MemorySegment value = MemorySegment.ofArray(body);
        Entry entry = new BaseEntry(key, value);
        dao.upsert(entry);
        return new Response(
                String.valueOf(HttpURLConnection.HTTP_CREATED),
                Response.EMPTY
        );
    }

    private Response handleDelete(String keyString) {
        MemorySegment key = MemorySegment.ofArray(keyString.getBytes(StandardCharsets.UTF_8));
        Entry entry = new BaseEntry(key, null);
        dao.upsert(entry);
        return new Response(
                String.valueOf(HttpURLConnection.HTTP_ACCEPTED),
                Response.EMPTY
        );
    }

    private Response handleGet(String keyString) throws IOException {
        MemorySegment key = MemorySegment.ofArray(keyString.getBytes(StandardCharsets.UTF_8));
        Entry<MemorySegment> value = dao.get(key);
        if (value != null) {
            if (value.value() == null)
                throw new RuntimeException();
            ByteBuffer buffer = value.value().asByteBuffer();
            byte[] arr = new byte[buffer.remaining()];
            buffer.get(arr);
            return new Response(
                    String.valueOf(HttpURLConnection.HTTP_OK),
                    arr
            );
        }

        return new Response(
                String.valueOf(HttpURLConnection.HTTP_NOT_FOUND),
                Response.EMPTY
        );
    }

    @Override
    public synchronized void start() {
        Config daoConfig = new Config(workDir, flushDaoThresholdBytes);
        requestHandlers = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        try {
            dao = new MemorySegmentDao(daoConfig);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        super.start();
    }

    @Override
    public synchronized void stop() {
        requestHandlers.shutdown();
        super.stop();

        for (SelectorThread thread : selectors) {
            for (Session session : thread.selector) {
                session.close();
            }
        }
        try {
            dao.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String convertStatusCode(int code) {
        switch (code) {
            case 200:
                return Response.OK;
            case 201:
                return Response.CREATED;
            case 500:
                return Response.INTERNAL_ERROR;
            case 400:
                return Response.BAD_REQUEST;
            case 405:
                return Response.METHOD_NOT_ALLOWED;
            case 202:
                return Response.ACCEPTED;
            case 404:
                return Response.NOT_FOUND;
            default:
                return Response.BAD_GATEWAY;
        }
    }
}
