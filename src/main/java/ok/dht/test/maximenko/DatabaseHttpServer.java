package ok.dht.test.maximenko;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.maximenko.dao.BorderedMergeIterator;
import ok.dht.test.maximenko.dao.Config;
import ok.dht.test.maximenko.dao.Entry;
import ok.dht.test.maximenko.dao.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.server.RejectedSessionException;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DatabaseHttpServer extends HttpServer {
    private static final int flushDaoThresholdBytes = 10000000; // 100 MB
    private static final int DELETED_RECORD_CODE = 409;
    private static final String REQUEST_PATH = "/v0/entity";
    private static final String NOT_SPREAD_HEADER = "notSpread";
    private static final String NOT_SPREAD_HEADER_VALUE = "true";
    private static final String TIME_HEADER = "time";
    private static final Response BAD_REQUEST = new Response(Response.BAD_REQUEST, Response.EMPTY);
    private static final Response NOT_FOUND = new Response(Response.NOT_FOUND, Response.EMPTY);
    private static final Logger LOGGER = Logger.getLogger(String.valueOf(DatabaseService.class));
    private MemorySegmentDao dao;
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
        List<Integer> nodesIds = IntStream.range(0, config.clusterUrls().size()).boxed().collect(Collectors.toList());
        keyDispatcher = new KeyDispatcher(nodesIds);
        clusterConfig = config.clusterUrls().toArray(new String[0]);
        clusterSize = config.clusterUrls().size();
        selfId = config.clusterUrls().indexOf(config.selfUrl());
        httpClient = HttpClient.newHttpClient();
    }

    @Override
    public HttpSession createSession(Socket socket) throws RejectedSessionException {
        return new HttpSession(socket, this) {
            @Override
            protected void writeResponse(Response response, boolean includeBody) throws IOException {
                if (response instanceof ChunkedResponse) {
                    ChunkedResponse chunkedResponse = (ChunkedResponse) response;
                    super.write(new QueueItem() {
                                    @Override
                                    public int write(Socket socket) throws IOException {
                                        byte[] body = chunkedResponse.getNextChunk();
                                        int written = socket.write(body, 0, body.length);
                                        if (chunkedResponse.last()) {
                                            body = chunkedResponse.finalChunk();
                                            written += socket.write(body, 0, body.length);
                                        }
                                        return written;
                                    }

                                    @Override
                                    public int remaining() {
                                        return chunkedResponse.last() ? 0 : 1;
                                    }
                                }
                    );
                } else {
                    super.writeResponse(response, includeBody);
                }
            }
        };
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        requestHandlers.execute(() -> handleRequestTask(request, session));
    }

    public void handleRequestTask(Request request, HttpSession session) {
        if (!request.getPath().equals(REQUEST_PATH)) {
            sendResponse(session, BAD_REQUEST);
            return;
        }

        String startString = request.getParameter("start=");
        if(HttpUtils.isArgumentCorrect(startString)) {
            handleRangeRequest(session, request, Integer.parseInt(startString));
        }

        String key = request.getParameter("id=");
        if (!HttpUtils.isArgumentCorrect(key)) {
            sendResponse(session, BAD_REQUEST);
            return;
        }

        String spreadHeaderValue = request.getHeader(NOT_SPREAD_HEADER);
        if (spreadHeaderValue != null && spreadHeaderValue.equals(": " + NOT_SPREAD_HEADER_VALUE)) {
            long time = HttpUtils.getTimeFromRequest(request);
            try {
                sendResponse(session, localRequest(key, request, time).get());
            } catch (Exception e) {
                LOGGER.severe("DAO get failure");
                sendResponse(session, new Response(Response.INTERNAL_ERROR, Response.EMPTY));
            }
            return;
        }

        handleMultiNodeRequest(session, request, key);
    }

    private void handleRangeRequest(HttpSession session, Request request, Integer start) {
        String endString = request.getParameter("end=");
        final Integer end = HttpUtils.isArgumentCorrect(endString)
                ? Integer.parseInt(endString) : null;

        List<Iterator<Entry<MemorySegment>>> allNodesIterators = getClusterRangeIterators(start, end);
        Iterator<Entry<MemorySegment>> mergeIterator;
        try {
            mergeIterator = new BorderedMergeIterator(allNodesIterators, allNodesIterators.size());
        } catch (IOException e) {
            sendResponse(session, new Response(Response.INTERNAL_ERROR, Response.EMPTY));
            return;
        }

        sendResponse(session, new ChunkedResponse(Response.OK, mergeIterator));
    }

    private List<Iterator<Entry<MemorySegment>>> getClusterRangeIterators(Integer start, Integer end) {
        List<Iterator<Entry<MemorySegment>>> result = new ArrayList<>();
        try {
            result.add(dao.get(null, null));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Dao get range error");
            return result;
        }

        return result;
    }

    private void handleMultiNodeRequest(HttpSession session, Request request, String key) {
        String replicasString = request.getParameter("from=");
        final int replicasAmount = HttpUtils.isArgumentCorrect(replicasString)
                ? Integer.parseInt(replicasString) : clusterSize;

        String quorumString = request.getParameter("ack=");
        final int quorum = HttpUtils.isArgumentCorrect(quorumString)
                ? Integer.parseInt(quorumString) : clusterSize / 2 + 1;

        if ((replicasAmount > clusterSize) || (quorum > replicasAmount) || (quorum == 0)) {
            sendResponse(session, BAD_REQUEST);
            return;
        }

        ArrayDeque<Integer> replicasIds = keyDispatcher.getReplicas(key, replicasAmount);
        CompletableFuture<Response> response;
        final Integer[] successCount = {0};
        final Integer[] responsesCollected = {0};
        final long[] mostRecentResponseTime = {0};
        final Boolean[] finished = {false};
        final Response[] resultResponse = {NOT_FOUND};
        long currentTime = (new Date()).getTime();
        for (Integer replica : replicasIds) {
            if (replica != selfId) {
                response = proxyRequest(replica, key, request, currentTime);
            } else {
                response = localRequest(key, request, currentTime);
            }

            response.thenAccept(currentResponse
                    -> handleNodeResponse(session, currentResponse, finished,
                    mostRecentResponseTime, resultResponse, successCount,
                    responsesCollected, replicasAmount, quorum));
        }
    }

    private static synchronized void handleNodeResponse(HttpSession session,
                                                        Response response,
                                                        final Boolean[] createdResponse,
                                                        long[] mostRecentResponseTime,
                                                        final Response[] successfulResponse,
                                                        final Integer[] successCount,
                                                        final Integer[] responsesCollected,
                                                        int replicasAmount,
                                                        int quorum) {
        long time = HttpUtils.getTimeFromResponse(response);
        int responseCode = response.getStatus();
        if (createdResponse[0]) {
            return;
        }
        if (responseCode < 500) {
            if (responseCode != 404) {
                if (mostRecentResponseTime[0] <= time) {
                    mostRecentResponseTime[0] = time;
                    successfulResponse[0] = response;
                }
            }
            successCount[0] += 1;
        }

        responsesCollected[0] += 1;
        if (responsesCollected[0] == replicasAmount || (successCount[0] >= quorum
                && successfulResponse[0].getStatus() != 404)) {
            responseWithRespectToQuorum(session, successfulResponse[0], successCount[0], quorum);
            createdResponse[0] = true;
        }
    }

    private static void responseWithRespectToQuorum(HttpSession session,
                                                    Response successfulResponse,
                                                    int successCount,
                                                    int quorum) {
        if (successCount >= quorum) {
            sendResponseToClient(session, successfulResponse);
        } else {
            sendResponseToClient(session, new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY));
        }
    }

    private static void sendResponse(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Response sending failed");
            session.close();
        }
    }

    private static void sendResponseToClient(HttpSession session, Response response) {
        if (response.getStatus() == DELETED_RECORD_CODE) {
            response = NOT_FOUND;
        }
        sendResponse(session, response);
    }

    private CompletableFuture<Response> localRequest(String key, Request request, long time) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return handleMethod(key, request.getMethod(), request.getBody(), time);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Service failed to handle local request");
                return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
            }
        });
    }

    private CompletableFuture<Response> proxyRequest(int targetNodeId, String key, Request request, long time) {
        byte[] requestBody = request.getBody();
        if (requestBody == null) {
            requestBody = new byte[0];
        }
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(clusterConfig[targetNodeId] + REQUEST_PATH + "?id=" + key))
                .timeout(Duration.ofSeconds(1))
                .method(request.getMethodName(), HttpRequest.BodyPublishers.ofByteArray(requestBody))
                .header(NOT_SPREAD_HEADER, NOT_SPREAD_HEADER_VALUE)
                .header("time", String.valueOf(time))
                .build();

        try {
            return CompletableFuture.supplyAsync(() -> {
                HttpResponse<byte[]> response;
                try {
                    response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
                } catch (Exception e) {
                    LOGGER.log(Level.INFO, "Failed to send request to another node");
                    return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
                }
                Optional<String> timeHeader = response.headers().firstValue(TIME_HEADER);
                Response result = new Response(HttpUtils.convertStatusCode(response.statusCode()), response.body());
                timeHeader.ifPresent(s -> result.addHeader(TIME_HEADER + ": " + s));
                return result;
            });
        } catch (Exception e) {
            LOGGER.log(Level.INFO, "Inter node interaction failure");
            return CompletableFuture.completedFuture(null);
        }
    }

    Response handleMethod(String key, int method, byte[] body, long time) throws IOException {
        return switch (method) {
            case Request.METHOD_GET -> handleGet(key);
            case Request.METHOD_DELETE -> handleDelete(key, time);
            case Request.METHOD_PUT -> handlePut(key, body, time);
            default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
        };
    }

    private Response handlePut(String keyString, byte[] body, long time) {
        timeDaoWrapper.put(keyString.getBytes(StandardCharsets.UTF_8), body, time);
        return new Response(String.valueOf(HttpURLConnection.HTTP_CREATED), Response.EMPTY);
    }

    private Response handleDelete(String keyString, long time) {
        timeDaoWrapper.delete(keyString.getBytes(StandardCharsets.UTF_8), time);
        return new Response(String.valueOf(HttpURLConnection.HTTP_ACCEPTED), Response.EMPTY);
    }

    private Response handleGet(String keyString) {
        ValueAndTime valueAndTime = timeDaoWrapper.get(keyString.getBytes(StandardCharsets.UTF_8));
        if (valueAndTime != null) {
            if (valueAndTime.value == null) {
                Response response = new Response(HttpUtils.convertStatusCode(DELETED_RECORD_CODE), Response.EMPTY);
                response.addHeader("time: " + valueAndTime.time);
                return response;
            }

            Response response = new Response(Response.OK, valueAndTime.value);
            response.addHeader("time: " + valueAndTime.time);
            return response;
        }

        return new Response(String.valueOf(HttpURLConnection.HTTP_NOT_FOUND), Response.EMPTY);
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
}
