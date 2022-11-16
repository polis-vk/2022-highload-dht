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
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
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
    private static final String RANGE_REQUEST_PATH = "/v0/entities";
    private static final Response BAD_REQUEST = new Response(Response.BAD_REQUEST, Response.EMPTY);
    private static final Response NOT_FOUND = new Response(Response.NOT_FOUND, Response.EMPTY);
    private static final Logger LOGGER = Logger.getLogger(String.valueOf(DatabaseHttpServer.class));
    private static final int REPLICA_FACTOR = 1;
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
                if (response instanceof ChunkedResponse chunkedResponse) {
                    super.write(new QueueItem() {
                                    private boolean init = false;

                                    @Override
                                    public int write(Socket socket) throws IOException {
                                        if (!init) {
                                            byte[] body = chunkedResponse.initialChunk();
                                            socket.write(body, 0, body.length);
                                            init = true;
                                        }
                                        if (chunkedResponse.hasNext()) {
                                            byte[] body = chunkedResponse.getNextChunk(chunkedResponse.toClient());
                                            return socket.write(body, 0, body.length);
                                        }
                                        return 0;
                                    }

                                    @Override
                                    public int remaining() {
                                        return chunkedResponse.hasNext() ? 1 : 0;
                                    }

                                    @Override
                                    public void release() {
                                        byte[] body = chunkedResponse.finalChunk();
                                        try {
                                            socket.write(body, 0, body.length);
                                        } catch (IOException e) {
                                            throw new RuntimeException(e);
                                        }
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
        if (request.getPath().equals(RANGE_REQUEST_PATH)) {
            handleRangeRequest(session, request);
            return;
        }

        if (!request.getPath().equals(REQUEST_PATH)) {
            sendResponse(session, BAD_REQUEST);
            return;
        }

        String key = request.getParameter("id=");
        if (!HttpUtils.isArgumentCorrect(key)) {
            sendResponse(session, BAD_REQUEST);
            return;
        }

        if (ClusterInteractions.isItRequestFRomOtherNode(request)) {
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

    private void handleRangeRequest(HttpSession session, Request request) {
        String start = request.getParameter("start=");
        String end = request.getParameter("end=");

        if (!HttpUtils.isArgumentCorrect(start)) {
            sendResponse(session, BAD_REQUEST);
            return;
        }

        List<Iterator<Entry<MemorySegment>>> nodesIterators;
        boolean toNode = ClusterInteractions.isItRequestFRomOtherNode(request);
        if (toNode) {
            nodesIterators = thisNodeIterator(start, end);
        } else {
            List<Integer> coveringNodes = keyDispatcher.getNodesCoverage(REPLICA_FACTOR);
            nodesIterators = getClusterRangeIterators(coveringNodes, start, end);
        }

        Iterator<Entry<MemorySegment>> mergeIterator;
        try {
            mergeIterator = new BorderedMergeIterator(nodesIterators, nodesIterators.size());
        } catch (IOException e) {
            sendResponse(session, new Response(Response.INTERNAL_ERROR, Response.EMPTY));
            return;
        }

        sendResponse(session, new ChunkedResponse(Response.OK, mergeIterator, !toNode));
    }

    private List<Iterator<Entry<MemorySegment>>> getClusterRangeIterators(List<Integer> coveringNodes,
                                                                          String startString,
                                                                          String endString) {
        List<Iterator<Entry<MemorySegment>>> result = thisNodeIterator(startString, endString);
        for (Integer node : coveringNodes) {
            if (node != selfId) {
                result.add(ClusterInteractions.getProxyRange(clusterConfig[node] + RANGE_REQUEST_PATH,
                        startString,
                        endString,
                        httpClient));
            }
        }

        return result;
    }



    private List<Iterator<Entry<MemorySegment>>> thisNodeIterator(String startString, String endString) {

        MemorySegment start = startString == null ? null :
                MemorySegment.ofArray(startString.getBytes(StandardCharsets.UTF_8));
        MemorySegment end = endString == null ? null :
                MemorySegment.ofArray(endString.getBytes(StandardCharsets.UTF_8));
        List<Iterator<Entry<MemorySegment>>> result = new ArrayList<>();
        try {
            result.add(timeDaoWrapper.get(start, end));
        } catch (Exception e) {
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
                response = ClusterInteractions.proxyRequest(clusterConfig[replica] + REQUEST_PATH,
                        key, request, currentTime, httpClient);
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
