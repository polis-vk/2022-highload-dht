package ok.dht.test.vihnin;

import ok.dht.ServiceConfig;
import one.nio.http.HttpClient;
import one.nio.http.HttpException;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.ConnectionString;
import one.nio.net.Session;
import one.nio.pool.Pool;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import one.nio.util.Utf8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static ok.dht.test.vihnin.ServiceUtils.emptyResponse;
import static ok.dht.test.vihnin.ServiceUtils.getHeaderValue;

public class ParallelHttpServer extends HttpServer {
    private static final Logger logger = LoggerFactory.getLogger(ParallelHttpServer.class);
    private static final String INNER_HEADER_NAME = "Inner";
    private static final String INNER_HEADER_VALUE = "True";

    public static final String TIME_HEADER_NAME = "Timestamp";

    // must be > 2
    private static final int VNODE_NUMBER_PER_SERVER = 5;
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

    public ParallelHttpServer(ServiceConfig config, ResponseManager responseManager, Object... routers) throws IOException {
        super(createConfigFromPort(config.selfPort()), routers);
        this.responseManager = responseManager;

        addRequestHandlers(this.responseManager);

        this.clusterManager = new ClusterManager(config.clusterUrls());
        this.shard = clusterManager.getShardByUrl(config.selfUrl());

        distributeVNodes(clusterManager.clusterSize());

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

        if (id == null) {
            session.sendResponse(emptyResponse(Response.BAD_REQUEST));
            return;
        }

        int ack;
        int from;

        if (ackRaw != null && fromRaw != null) {
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
        } else {
            from = clusterManager.clusterSize();
            ack = clusterManager.clusterSize() / 2 + 1;
        }

        int destinationShardId = getShardId(id);
        String innerHeaderValue = getHeaderValue(request, INNER_HEADER_NAME);

        try {
            if (destinationShardId == shard) {
                executorService.submit(() -> {
                    requestTask(request, session, ack, from);
                });
            } else if (innerHeaderValue != null && innerHeaderValue.equals(INNER_HEADER_VALUE)) {
                executorService.submit(() -> {
                    handleForeignRequest(request, session);
                });
            } else {
                internalRequestService.submit(() -> {
                    handleInnerRequest(request, session, clusterManager.getUrlByShard(destinationShardId));
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

    private void handleInnerRequest(Request request, HttpSession session, String destinationUrl) {
        try {
            Response response = handleRedirectRequest(request, destinationUrl, false);
            if (response != null) {
                session.sendResponse(response);
            } else {
                session.sendError(Response.SERVICE_UNAVAILABLE, "");
            }
        } catch (IOException e) {
            handleException(session, e);
        }
    }

    private Response handleAckRequest(Request request, String destinationUrl) {
        return handleRedirectRequest(request, destinationUrl, true);
    }

    private Response handleRedirectRequest(
            Request request,
            String destinationUrl,
            boolean addFlag) {
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

            if (addFlag) {
                req.addHeader(INNER_HEADER_NAME + ": " + INNER_HEADER_VALUE);
            }
            return clients.get().get(destinationUrl).invoke(req);
        } catch (Exception e) {
            return null;
        }
    }

    private int getShardId(String key) {
        return shardHelper.getShardByKey(key);
    }

    private void requestTask(Request request, HttpSession session, int ack, int from) {
        long currentTime = System.currentTimeMillis();
        request.addHeader(TIME_HEADER_NAME + ": " + currentTime);

        Map<String, Long> timestamps = new HashMap<>();
        Map<String, byte[]> data = new HashMap<>();
        Map<String, Integer> statuses = new HashMap<>();

        int actualAck = 0;

        Response myResponse = responseManager.handleRequest(request);

        if (myResponse != null) {
            String value = getHeaderValue(myResponse, TIME_HEADER_NAME);
            if (value != null) {
                String currShard = clusterManager.getUrlByShard(shard);
                timestamps.put(currShard, Long.parseLong(value));
                data.put(currShard, myResponse.getBody());
                statuses.put(currShard, myResponse.getStatus());
            }
            actualAck += 1;
        }


        for (var neighbor : clusterManager.getNeighbours(shard).subList(0, from - 1)) {
            Response handleSuccess = handleAckRequest(request, neighbor);
            if (handleSuccess != null) {
                String value = getHeaderValue(handleSuccess, TIME_HEADER_NAME);
                if (value != null) {
                    timestamps.put(neighbor, Long.parseLong(value));
                    data.put(neighbor, handleSuccess.getBody());
                    statuses.put(neighbor, handleSuccess.getStatus());
                }
                actualAck += 1;
            }
            if (actualAck == ack) {
                break;
            }
        }
        try {
            if (actualAck < ack) {
                session.sendResponse(emptyResponse("504 Not Enough Replicas"));
            } else {
                if (request.getMethod() == Request.METHOD_DELETE) {
                    session.sendResponse(emptyResponse("202 Accepted"));
                } else if (request.getMethod() == Request.METHOD_PUT) {
                    session.sendResponse(emptyResponse("201 Created"));
                } else if (request.getMethod() == Request.METHOD_GET) {
                    String freshestUrl = null;
                    long freshestTime = -1;
                    for (Map.Entry<String, Long> entry : timestamps.entrySet()) {
                        if (entry.getValue() > freshestTime) {
                            freshestTime = entry.getValue();
                            freshestUrl = entry.getKey();
                        }
                    }
                    if (statuses.get(freshestUrl) != 200) {
                        session.sendResponse(emptyResponse("404 Not Found"));
                    } else {
                        session.sendResponse(new Response("200 OK", data.get(freshestUrl)));
                    }
                }
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

    private void distributeVNodes(int numberOfServers) {
        int gapPerNode = 2 * (Integer.MAX_VALUE / VNODE_NUMBER_PER_SERVER);
        int gapPerServer = gapPerNode / numberOfServers;

        for (int i = 0; i < numberOfServers; i++) {
            Set<Integer> vnodes = new HashSet<>();
            int currVNode = Integer.MIN_VALUE + i * gapPerServer;
            for (int j = 0; j < VNODE_NUMBER_PER_SERVER; j++) {
                vnodes.add(currVNode);
                currVNode += gapPerNode;
            }
            shardHelper.addShard(i, vnodes);
        }
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }
}
