package ok.dht.test.maximenko;

import ok.dht.ServiceConfig;
import ok.dht.test.maximenko.dao.Config;
import ok.dht.test.maximenko.dao.Dao;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class DatabaseHttpServer extends HttpServer {
    private static final int flushDaoThresholdBytes = 10000000;
    private static final int DELETED_RECORD_MESSAGE = 409;
    private static final String REQUEST_PATH = "/v0/entity";
    private static final String NOT_SPREAD_HEADER = "notSpread";
    private static final String NOT_SPREAD_HEADER_VALUE = "true";
    private static final String TIME_HEADER = "time";
    private static final Response BAD_REQUEST = new Response(
            Response.BAD_REQUEST,
            Response.EMPTY
    );
    private static final Logger LOGGER = Logger.getLogger(String.valueOf(DatabaseService.class));
    private Dao dao;
    private TimeDaoWrapper timeDaoWrapper;

    private final KeyDispatcher keyDispatcher;
    private final HttpClient httpClient;
    private ExecutorService requestHandlers;
    private final String[] clusterConfig;
    private final int clusterSize;
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
            handleRequestTask(request, session);
        });
    }

    public void handleRequestTask(Request request, HttpSession session) {
        if (!request.getPath().equals(REQUEST_PATH)) {
            sendResponse(session, BAD_REQUEST);
            return;
        }

        String key = request.getParameter("id=");
        if (key == null || key.equals("")) {
            sendResponse(session, BAD_REQUEST);
            return;
        }

        String spreadHeaderValue = request.getHeader(NOT_SPREAD_HEADER);
        if (spreadHeaderValue != null && spreadHeaderValue.equals(": " + NOT_SPREAD_HEADER_VALUE)) {
            long time = Long.parseLong(request.getHeader(TIME_HEADER + ": "));
            try {
                sendResponse(session, localRequest(key, request, time).get());
            } catch (Exception e) {
                LOGGER.severe("DAO get failure");
                sendResponse(session, new Response(Response.INTERNAL_ERROR, Response.EMPTY));
            }
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
            sendResponse(session, BAD_REQUEST);
            return;
        }

        ArrayDeque<Integer> replicasIds = keyDispatcher.getReplicas(key, replicasAmount);
        CompletableFuture<Response> response;
        final Integer[] successCount = {0};
        final Integer[] responsesCollected = {0};
        final long[] mostRecentResponseTime = {0};
        AtomicBoolean createdResponse = new AtomicBoolean(false);
        final AtomicReference<Response>[] successfulResponse
                = new AtomicReference[]{new AtomicReference<>(new Response(Response.NOT_FOUND, Response.EMPTY))};
        Date date = new Date();
        long currentTime = date.getTime();
        for (Integer replica : replicasIds) {
            if (replica != selfId) {
                response = proxyRequest(replica, key, request, currentTime);
            } else {
                response = localRequest(key, request, currentTime);
            }

            final int finalReplicasAmount = replicasAmount;
            final int finalQuorum = quorum;
            response.thenAccept(currentResponse -> {
                long time = getTimeFromResponse(currentResponse);
                int responseCode = currentResponse.getStatus();
                synchronized (this) {
                    if (createdResponse.get()) {
                        return;
                    }
                    if (responseCode < 500) {
                        if (responseCode != 404) {
                            if (mostRecentResponseTime[0] <= time) {
                                mostRecentResponseTime[0] = time;
                                successfulResponse[0].set(currentResponse);
                            }
                        }
                        successCount[0] += 1;
                    }

                    responsesCollected[0] += 1;
                    if (responsesCollected[0] == finalReplicasAmount
                            || (successCount[0] >= finalQuorum
                                    && successfulResponse[0].get().getStatus() != 404)) {
                        sendAnswerToClient(session, successfulResponse[0].get(), successCount[0], finalQuorum);
                        createdResponse.set(true);
                    }
                }
            });
        }
    }

    private void sendResponse(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e) {
            LOGGER.severe("Response sending failed");
            session.close();
        }
    }

    void sendAnswerToClient(HttpSession session, Response successfulResponse, int successCount, int quorum) {
        if (successCount >= quorum) {
            sendResponseToClient(session, successfulResponse);
        } else {
            sendResponseToClient(session, new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY));
        }
    }

    private void sendResponseToClient(HttpSession session, Response response) {
        if (response.getStatus() == DELETED_RECORD_MESSAGE) {
            response = new Response(Response.NOT_FOUND, Response.EMPTY);
        }
       sendResponse(session, response);
    }
    private long getTimeFromResponse(Response response) {
        String timeHeader = response.getHeader(TIME_HEADER + ": ");
        if (timeHeader == null || timeHeader == "") {
            return 0;
        }
        return Long.parseLong(timeHeader);
    }
    private CompletableFuture<Response> localRequest(String key, Request request, long time) {
        CompletableFuture<Response> result = CompletableFuture.supplyAsync(()-> {
            try {
                Response response = handleMethod(key, request.getMethod(), request.getBody(), time);
                return response;
            } catch (IOException e) {
                LOGGER.severe("Service failed to handle local request");
                return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
            }
        });
        return result;
    }

    private CompletableFuture<Response> proxyRequest(int targetNodeId, String key, Request request, long time) {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(clusterConfig[targetNodeId] + REQUEST_PATH + "?id=" + key))
                .timeout(Duration.ofSeconds(1))
                .method(request.getMethodName(), HttpRequest.BodyPublishers.ofByteArray(request.getBody()))
                .header(NOT_SPREAD_HEADER, NOT_SPREAD_HEADER_VALUE)
                .header("time", String.valueOf(time))
                .build();

        try {
            CompletableFuture<Response> future = CompletableFuture.supplyAsync(()-> {
                HttpResponse<byte[]> response;
                try {
                    response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
                } catch (Exception e) {
                    LOGGER.severe("Failed to send request to another node");
                    return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
                }
                Optional<String> timeHeader = response.headers().firstValue(TIME_HEADER);
                Response result = new Response(convertStatusCode(response.statusCode()), response.body());
                if (timeHeader.isPresent()) {
                    result.addHeader(TIME_HEADER + ": " + timeHeader.get());
                }
                return result;
            });
            return future;
        } catch (Exception e) {
            LOGGER.severe("Inter node interaction failure");
            return CompletableFuture.completedFuture(null);
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
            LOGGER.severe("Can't start database");
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
            LOGGER.severe("Can't gracefully stop database");
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
