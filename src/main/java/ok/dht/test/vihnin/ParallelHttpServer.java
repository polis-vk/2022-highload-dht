package ok.dht.test.vihnin;

import ok.dht.ServiceConfig;
import ok.dht.test.vihnin.database.Row;
import ok.dht.test.vihnin.utils.ServerUtils;
import ok.dht.test.vihnin.utils.ServiceUtils;
import one.nio.http.HttpException;
import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.server.RejectedSessionException;
import one.nio.server.SelectorThread;
import one.nio.util.ByteArrayBuilder;
import one.nio.util.Utf8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static ok.dht.test.vihnin.utils.ServerUtils.handleSingleAcknowledgment;
import static ok.dht.test.vihnin.utils.ServerUtils.methodAllowed;
import static ok.dht.test.vihnin.utils.ServiceUtils.emptyResponse;

public class ParallelHttpServer extends HttpServer {
    private static final Logger logger = LoggerFactory.getLogger(ParallelHttpServer.class);
    public static final String INNER_HEADER_NAME = "Inner";
    public static final String INNER_HEADER_VALUE = "True";

    public static final String TIME_HEADER_NAME = "Timestamp";
    private static final int WORKERS_NUMBER = 4;
    private static final int QUEUE_CAPACITY = 228;

    private static final int INTERNAL_WORKERS_NUMBER = 4;
    private static final int INTERNAL_QUEUE_CAPACITY = 2048;

    private final ShardHelper shardHelper = new ShardHelper();
    private final ClusterManager clusterManager;
    private final Integer shard;

    private final ResponseManager responseManager;

    private final ThreadLocal<Map<String, HttpClient>> javaClients;

    private final ExecutorService executorService = new ThreadPoolExecutor(
            WORKERS_NUMBER,
            WORKERS_NUMBER,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY)
    );

    private final ExecutorService internalRequestService = new ThreadPoolExecutor(
            INTERNAL_WORKERS_NUMBER,
            INTERNAL_WORKERS_NUMBER,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(INTERNAL_QUEUE_CAPACITY)
    );

    public ParallelHttpServer(
            ServiceConfig config,
            ResponseManager responseManager,
            Object... routers) throws IOException {
        super(ServiceUtils.createConfigFromPort(config.selfPort()), routers);
        this.responseManager = responseManager;

        addRequestHandlers(this.responseManager);

        this.clusterManager = new ClusterManager(config.clusterUrls());
        this.shard = clusterManager.getShardByUrl(config.selfUrl());

        ServiceUtils.distributeVNodes(shardHelper, clusterManager.clusterSize());

        this.javaClients = ThreadLocal.withInitial(() -> {
            Map<String, HttpClient> baseMap = new HashMap<>();
            for (String url : config.clusterUrls()) {
                baseMap.put(url, HttpClient.newBuilder().executor(executorService).build());
            }
            return baseMap;
        });
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (!methodAllowed(request.getMethod())) {
            session.sendResponse(emptyResponse(Response.METHOD_NOT_ALLOWED));
            return;
        }

        if (ServiceUtils.ENDPOINT.equals(request.getPath())) {
            handleSingleValueRequest(request, session);
        } else if (ServiceUtils.RANGE_ENDPOINT.equals(request.getPath())) {
            handleRangeRequest(request, session);
        } else {
            session.sendResponse(emptyResponse(Response.BAD_REQUEST));
        }
    }

    private void handleSingleValueRequest(Request request, HttpSession session) throws IOException {
        String id = request.getParameter("id=");
        String ackRaw = request.getParameter("ack=");
        String fromRaw = request.getParameter("from=");

        if (id == null || id.isEmpty()) {
            session.sendResponse(emptyResponse(Response.BAD_REQUEST));
            return;
        }

        int ack;
        int from;

        if (ackRaw == null || fromRaw == null) {
            from = clusterManager.clusterSize();
            ack = clusterManager.clusterSize() / 2 + 1;
        } else {
            try {
                ack = Integer.parseInt(ackRaw);
                from = Integer.parseInt(fromRaw);
            } catch (NumberFormatException e) {
                session.sendResponse(
                        new Response(
                                Response.BAD_REQUEST,
                                Utf8.toBytes("ack and from must be integers")
                        )
                );
                return;
            }
        }

        if (ack > from || ack < 1) {
            session.sendResponse(emptyResponse(Response.BAD_REQUEST));
            return;
        }

        int destinationShardId = getShardId(id);
        String innerHeaderValue = ServiceUtils.getHeaderValue(request, INNER_HEADER_NAME);

        try {
            if (innerHeaderValue == null) {
                internalRequestService.submit(() -> {
                    requestTask(request, session, destinationShardId, ack, from);
                });
            } else {
                executorService.submit(() -> {
                    handleForeignRequest(request, session);
                });
            }
        } catch (RejectedExecutionException e) {
            logger.error(e.getMessage());
            session.sendError(
                    Response.SERVICE_UNAVAILABLE,
                    "Handling was rejected due to some internal problem");
        }
    }

    private void handleRangeRequest(Request request, HttpSession session) throws IOException {
        String start = request.getParameter("start=");
        String end = request.getParameter("end=");

        if (start == null) {
            session.sendResponse(emptyResponse(Response.BAD_REQUEST));
            return;
        }

        try {
            executorService.submit(() -> {
                responseByManager(session, start, end);
            });
        } catch (RejectedExecutionException e) {
            logger.error(e.getMessage());
            session.sendError(
                    Response.SERVICE_UNAVAILABLE,
                    "Handling was rejected due to some internal problem");
        }
    }

    private void responseByManager(HttpSession session, String start, String end) {
        try {
            session.sendResponse(responseManager.handleGetRange(start, end));
        } catch (IOException e) {
            handleException(session, e);
        }
    }

    private void handleForeignRequest(Request request, HttpSession session) {
        try {
            super.handleRequest(request, session);
        } catch (IOException e) {
            handleException(session, e);
        }
    }

    private void handleAckRequest(Request request, String destinationUrl, ResponseAccumulator responseAccumulator) {
        try {
            HttpRequest javaRequest = ServiceUtils.createJavaRequest(request, destinationUrl);

            javaClients.get().get(destinationUrl)
                    .sendAsync(javaRequest, HttpResponse.BodyHandlers.ofByteArray())
                    .handleAsync((httpResponse, throwable) ->
                            handleSingleAcknowledgment(responseAccumulator, httpResponse, throwable),
                            internalRequestService
                    );

        } catch (Exception e) {
            logger.error(e.getMessage());
            responseAccumulator.acknowledgeMissed();
        }
    }

    private int getShardId(String key) {
        return shardHelper.getShardByKey(key);
    }

    private void requestTask(Request request, HttpSession session, int destinationShardId, int ack, int from) {
        long currentTime = System.currentTimeMillis();
        request.addHeader(TIME_HEADER_NAME + ": " + currentTime);
        ResponseAccumulator responseAccumulator = new ResponseAccumulator(session, request.getMethod(), ack, from);

        for (var neighbor : clusterManager.getNeighbours(destinationShardId).subList(0, from)) {
            if (neighbor.equals(clusterManager.getUrlByShard(shard))) {
                Response handleSuccess = responseManager.handleRequest(request);
                if (handleSuccess == null) {
                    responseAccumulator.acknowledgeFailed();
                } else {
                    String value = ServiceUtils.getHeaderValue(handleSuccess, TIME_HEADER_NAME);
                    if (value == null) {
                        responseAccumulator.acknowledgeFailed();
                    } else {
                        long currTime = Long.parseLong(value);
                        responseAccumulator.acknowledgeSucceed(
                                currTime,
                                handleSuccess.getStatus(),
                                handleSuccess.getBody());
                    }
                }
            } else {
                handleAckRequest(request, neighbor, responseAccumulator);
            }
        }
    }

    public static void handleException(HttpSession session, Exception e) {
        // ask about it on lecture
        if (e instanceof HttpException) {
            try {
                session.sendResponse(emptyResponse(Response.BAD_REQUEST));
            } catch (IOException ex) {
                logger.error(ex.getMessage());
            }
        } else if (e instanceof IOException) {
            try {
                session.sendError(
                        Response.INTERNAL_ERROR,
                        "Handling interrupted by some internal error");
            } catch (IOException ex) {
                logger.error(ex.getMessage());
            }
        }
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        session.sendResponse(emptyResponse(Response.BAD_REQUEST));
    }

    @Override
    public synchronized void stop() {
        executorService.shutdown();
        internalRequestService.shutdown();

        for (SelectorThread selectorThread : selectors) {
            for (Session session : selectorThread.selector) {
                session.close();
            }
        }

        super.stop();

    }

    @Override
    public HttpSession createSession(Socket socket) throws RejectedSessionException {
        return new HttpSession(socket, this) {
            @Override
            protected void writeResponse(Response response, boolean includeBody) throws IOException {
                if (response instanceof ServerUtils.ChunkedResponse chunkedResponse) {
                    super.writeResponse(response, false);
                    var rows = chunkedResponse.iterator;
                    boolean last = false;

                    while (true) {
                        if (!rows.hasNext()) {
                            if (last) {
                                break;
                            } else {
                                last = true;
                            }
                        }

                        Row<String, byte[]> row = last
                                ? null
                                : rows.next();

                        super.write(new ServerUtils.ChunkItem(row));
                    }

                } else {
                    super.writeResponse(response, includeBody);
                }
            }
        };
    }
}
