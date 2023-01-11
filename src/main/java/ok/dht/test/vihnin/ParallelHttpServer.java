package ok.dht.test.vihnin;

import ok.dht.ServiceConfig;
import one.nio.http.HttpClient;
import one.nio.http.HttpException;
import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.ConnectionString;
import one.nio.net.Session;
import one.nio.pool.Pool;
import one.nio.server.SelectorThread;
import one.nio.util.Utf8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static ok.dht.test.vihnin.ServiceUtils.createConfigFromPort;
import static ok.dht.test.vihnin.ServiceUtils.distributeVNodes;
import static ok.dht.test.vihnin.ServiceUtils.emptyResponse;
import static ok.dht.test.vihnin.ServiceUtils.getHeaderValue;

public class ParallelHttpServer extends HttpServer {
    private static final Logger logger = LoggerFactory.getLogger(ParallelHttpServer.class);
    private static final String INNER_HEADER_NAME = "Inner";
    private static final String INNER_HEADER_VALUE = "True";

    public static final String TIME_HEADER_NAME = "Timestamp";
    private static final int WORKERS_NUMBER = 8;
    private static final int QUEUE_CAPACITY = 100;

    private static final int INTERNAL_WORKERS_NUMBER = 4;
    private static final int INTERNAL_QUEUE_CAPACITY = 100;

    private final ShardHelper shardHelper = new ShardHelper();
    private final ClusterManager clusterManager;
    private final Integer shard;

    private final ResponseManager responseManager;

    private final ThreadLocal<Map<String, HttpClient>> clients;

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
        super(createConfigFromPort(config.selfPort()), routers);
        this.responseManager = responseManager;

        addRequestHandlers(this.responseManager);

        this.clusterManager = new ClusterManager(config.clusterUrls());
        this.shard = clusterManager.getShardByUrl(config.selfUrl());

        distributeVNodes(shardHelper, clusterManager.clusterSize());

        this.clients = ThreadLocal.withInitial(() -> {
            Map<String, HttpClient> baseMap = new HashMap<>();
            for (String url : config.clusterUrls()) {
                baseMap.put(url, new HttpClient(new ConnectionString(url)));
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
        String innerHeaderValue = getHeaderValue(request, INNER_HEADER_NAME);

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
            session.sendError(
                    Response.SERVICE_UNAVAILABLE,
                    "Handling was rejected due to some internal problem");
        }
    }

    private void handleForeignRequest(Request request, HttpSession session) {
        try {
            super.handleRequest(request, session);
        } catch (IOException e) {
            handleException(session, e);
        }
    }

    private static boolean methodAllowed(int method) {
        return method == Request.METHOD_GET
                || method == Request.METHOD_DELETE
                || method == Request.METHOD_PUT;
    }

    private Response handleAckRequest(Request request, String destinationUrl) {
        try {
            Request req = new Request(
                    request.getMethod(),
                    request.getURI(),
                    true
            );

            req.setBody(request.getBody());
            Arrays.stream(request.getHeaders())
                    .filter(Objects::nonNull)
                    .forEach(req::addHeader);

            req.addHeader(INNER_HEADER_NAME + ": " + INNER_HEADER_VALUE);

            return clients.get().get(destinationUrl).invoke(req);
        } catch (Exception e) {
            return null;
        }
    }

    private int getShardId(String key) {
        return shardHelper.getShardByKey(key);
    }

    private void requestTask(Request request, HttpSession session, int destinationShardId, int ack, int from) {
        long currentTime = System.currentTimeMillis();
        request.addHeader(TIME_HEADER_NAME + ": " + currentTime);

        long freshestTime = -1;
        int freshestStatus = -1;
        byte[] freshestData = null;

        int actualAck = 0;

        for (var neighbor : clusterManager.getNeighbours(destinationShardId).subList(0, from)) {
            Response handleSuccess = neighbor.equals(clusterManager.getUrlByShard(shard))
                    ? responseManager.handleRequest(request)
                    : handleAckRequest(request, neighbor);
            if (handleSuccess != null) {
                String value = getHeaderValue(handleSuccess, TIME_HEADER_NAME);
                if (value != null) {
                    long currTime = Long.parseLong(value);

                    if (currTime >= freshestTime) {
                        freshestData = handleSuccess.getBody();
                        freshestStatus = handleSuccess.getStatus();
                        freshestTime = currTime;
                    }
                }
                actualAck += 1;
            }
        }

        processAcknowledgment(request, session, ack <= actualAck, freshestStatus, freshestData);
    }

    private static void processAcknowledgment(
            Request request,
            HttpSession session,
            boolean reachAckNumber,
            int freshestStatus,
            byte[] freshestData) {
        try {
            if (reachAckNumber) {
                if (request.getMethod() == Request.METHOD_DELETE) {
                    session.sendResponse(emptyResponse("202 Accepted"));
                } else if (request.getMethod() == Request.METHOD_PUT) {
                    session.sendResponse(emptyResponse("201 Created"));
                } else if (request.getMethod() == Request.METHOD_GET) {
                    if (freshestStatus == 200) {
                        session.sendResponse(new Response("200 OK", freshestData));
                    } else {
                        session.sendResponse(emptyResponse("404 Not Found"));
                    }
                }
            } else {
                session.sendResponse(emptyResponse("504 Not Enough Replicas"));
            }
        } catch (IOException e) {
            handleException(session, e);
        }
    }

    private static void handleException(HttpSession session, Exception e) {
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

        clients.get().values().forEach(Pool::close);

        for (SelectorThread selectorThread : selectors) {
            for (Session session : selectorThread.selector) {
                session.close();
            }
        }

        super.stop();

    }
}
