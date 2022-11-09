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
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class DatabaseHttpServer extends HttpServer {
    static final int flushDaoThresholdBytes = 10000000;
    private static final int DELETED_RECORD_MESSAGE = 409;
    private final static String requestPath = "/v0/entity";
    static final private Response badRequest = new Response(
            Response.BAD_REQUEST,
            Response.EMPTY
    );
    private Dao dao;
    private TimeDaoWrapper timeDaoWrapper;

    private final KeyDispatcher keyDispatcher;
    private final HttpClient httpClient;
    private ExecutorService requestHandlers;
    private final String[] clusterConfig;
    private final int clusterSize;
    private static final String notSpreadHeader = "notSpread";
    private static final String notSpreadHeaderValue = "true";
    private final java.nio.file.Path workDir;
    private final int selfId;
    public DatabaseHttpServer(HttpServerConfig httpConfig, ServiceConfig config) throws IOException {
        super(httpConfig);
        this.workDir = config.workingDir();
        List<Integer> nodesIds = IntStream.range(0, config.clusterUrls().size())
                                        .boxed()
                .                       collect(Collectors.toList());
        keyDispatcher = new KeyDispatcher(nodesIds);
        clusterConfig = config.clusterUrls().toArray(new String[0]);
        clusterSize = config.clusterUrls().size();
        selfId = config.clusterUrls().indexOf(config.selfUrl());
        httpClient = HttpClient.newHttpClient();
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        requestHandlers.execute(()-> {
                    try {
                        handleRequestTask(request, session);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
        );

    }

    public void handleRequestTask(Request request, HttpSession session) throws IOException {
        if (!request.getPath().equals(requestPath)) {
            session.sendResponse(badRequest);
            return;
        }

        String key = request.getParameter("id=");
        if (key == null || key.equals("")) {
            session.sendResponse(badRequest);
            return;
        }

        String spreadHeaderValue = request.getHeader(notSpreadHeader);
        if (spreadHeaderValue != null && spreadHeaderValue.equals(": " + notSpreadHeaderValue)) {
            long time = Long.parseLong(request.getHeader("time: "));
            session.sendResponse(localRequest(key, request, time));
            return;
        }

        String replicasString = request.getParameter("from=");
        int replicasAmount = clusterSize;
        if (replicasString != null && !replicasString.equals("")) {
            replicasAmount = Integer.valueOf(replicasString);
        }

        String quorumString = request.getParameter("ack=");
        int quorum = clusterSize / 2 + 1;
        if (quorumString != null && !quorumString.equals("")) {
            quorum = Integer.valueOf(quorumString);
        }

        if ((replicasAmount > clusterSize) || (quorum > replicasAmount) || (quorum == 0)) {
            session.sendResponse(badRequest);
        }

        ArrayDeque<Integer> replicasIds = keyDispatcher.getReplicas(key, replicasAmount);
        Integer successCount = 0;
        Response response;
        Response successfulResponse = new Response(Response.NOT_FOUND, Response.EMPTY);
        long mostRecentResponseTime = 0;
        Date date = new Date();
        long currentTime = date.getTime();
        for (Integer replica : replicasIds) {
            if (replica != selfId) {
                response = proxyRequest(replica, key, request, currentTime);
            } else {
                response = localRequest(key, request, currentTime);
                if (response.getStatus() == DELETED_RECORD_MESSAGE) {
                    response = new Response(Response.NOT_FOUND, Response.EMPTY);
                }
            }

            int responseCode = response.getStatus();
            if (responseCode < 500) {
                if (responseCode != 404) {
                    long time = getTimeFromResponse(response);
                    if (mostRecentResponseTime <= time) {
                        successfulResponse = response;
                        if (response.getStatus() == DELETED_RECORD_MESSAGE) {
                            successfulResponse = new Response(Response.NOT_FOUND, Response.EMPTY);
                        }
                    }
                }
                successCount += 1;
            }
        }

        if (successCount >= quorum) {
            try {
                session.sendResponse(successfulResponse);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            try {
                session.sendResponse(new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private long getTimeFromResponse(Response response) {
        String timeHeader = response.getHeader("time: ");
        if (timeHeader == null || timeHeader == "") {
            return 0;
        }
        return Long.parseLong(timeHeader);
    }
    private Response localRequest(String key, Request request, long time) {
        try {
            return handleMethod(key, request.getMethod(), request.getBody(), time);
        } catch (IOException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    private Response proxyRequest(int targetNodeId, String key, Request request, long time) {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(clusterConfig[targetNodeId] + requestPath + "?id=" + key))
                .timeout(Duration.ofSeconds(1))
                .method(request.getMethodName(), HttpRequest.BodyPublishers.ofByteArray(request.getBody()))
                .header(notSpreadHeader, notSpreadHeaderValue)
                .header("time", String.valueOf(time))
                .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            return new Response(convertStatusCode(response.statusCode()), response.body());
        } catch (Exception e) {
            return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
        }
    }

    Response handleMethod(String key, int method, byte[] body, long time) throws IOException {
        switch (method) {
            case Request.METHOD_GET:
                return handleGet(key);
            case Request.METHOD_DELETE:
                return handleDelete(key, time);
            case Request.METHOD_PUT:
                return handlePut(key, body, time);
            default:
                return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
        }
    }

    private Response handlePut(String keyString, byte[] body, long time) {
        timeDaoWrapper.put(keyString.getBytes(StandardCharsets.UTF_8), body, time);
        return new Response(
                String.valueOf(HttpURLConnection.HTTP_CREATED),
                Response.EMPTY
        );
    }

    private Response handleDelete(String keyString, long time) {
        timeDaoWrapper.delete(keyString.getBytes(StandardCharsets.UTF_8), time);
        return new Response(
                String.valueOf(HttpURLConnection.HTTP_ACCEPTED),
                Response.EMPTY
        );
    }

    private Response handleGet(String keyString) {
        ValueAndTime valueAndTime = timeDaoWrapper.get(keyString.getBytes(StandardCharsets.UTF_8));
        if (valueAndTime != null) {
            if (valueAndTime.value == null) {
                Response response = new Response(
                        convertStatusCode(DELETED_RECORD_MESSAGE),
                        Response.EMPTY
                );
                response.addHeader("time: " + valueAndTime.time);
                return response;
            }

            Response response = new Response(
                    Response.OK,
                    valueAndTime.value
            );
            response.addHeader("time: " + valueAndTime.time);
            return response;
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
            timeDaoWrapper = new TimeDaoWrapper(dao);
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
            case 409:
                return Response.CONFLICT;
            default:
                return Response.BAD_GATEWAY;
        }
    }
}
