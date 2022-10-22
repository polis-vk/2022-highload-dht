package ok.dht.test.lutsenko.service;

import ch.qos.logback.core.joran.action.SiftAction;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.lutsenko.dao.common.DaoConfig;
import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;
import one.nio.util.Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

public class DemoService implements Service {

    private static final int MAX_NODES_NUMBER = 1000; // was limited in lection
    private static final int VIRTUAL_NODES_NUMBER = 10;
    private static final int HASH_SPACE = MAX_NODES_NUMBER * VIRTUAL_NODES_NUMBER * 360;
    private static final String DAO_PREFIX = "dao";
    private static final String REQUEST_PATH = "/v0/entity";
    private static final Logger LOG = LoggerFactory.getLogger(DemoService.class);

    private final Path daoPath;
    private final ServiceConfig config;
    private final int selfNodeNumber;
    private final Map<Integer, String> nodesNumberToUrlMap = new HashMap<>();
    private final NavigableMap<Integer, Integer> virtualNodes = new TreeMap<>(); // node position to node number

    private HttpServer server; // Non-final due to stop() and possible restart in start()
    private ExecutorService requestExecutor; // Non-final due to shutdown in stop() and possible restart in start()
    private DaoHandler daoHandler; // Non-final due to close in stop() and possible restart in start()
    private ProxyHandler proxyHandler; // Non-final due to close in stop() and possible restart in start()

    public DemoService(ServiceConfig config) {
        if (config.clusterUrls().size() > MAX_NODES_NUMBER) {
            throw new IllegalArgumentException("There can`t be more " + MAX_NODES_NUMBER + " nodes");
        }
        this.config = config;
        this.daoPath = config.workingDir().resolve(DAO_PREFIX);
        this.selfNodeNumber = config.clusterUrls().indexOf(config.selfUrl());
        List<String> clusterUrls = config.clusterUrls();
        for (String url : clusterUrls) {
            nodesNumberToUrlMap.put(clusterUrls.indexOf(url), url);
        }
        fillVirtualNodes();
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        requestExecutor = RequestExecutorService.requestExecutorDiscardOldest();
        daoHandler = new DaoHandler(DaoConfig.defaultConfig(daoPath));
        proxyHandler = new ProxyHandler();

        server = new HttpServer(ServiceUtils.createConfigFromPort(config.selfPort())) {

            @Override
            public void handleRequest(Request request, HttpSession session) {
                String path = request.getPath();
                if (!path.equals(REQUEST_PATH)) {
                    ServiceUtils.sendResponse(session, Response.BAD_REQUEST);
                    return;
                }
                long requestTime = System.currentTimeMillis();
                requestExecutor.execute(new SessionRunnable(session, () -> {
                    String id = request.getParameter("id=");
                    if (id == null || id.isBlank()) {
                        ServiceUtils.sendResponse(session, Response.BAD_REQUEST);
                        return;
                    }
                    String proxyHeader = request.getHeader("Proxy");
                    if (proxyHeader != null) {
                        // substring(2) due to ": " before value
                        daoHandler.handle(id, request, session, Long.valueOf(proxyHeader.substring(2)));
                        return;
                    }
                    String ackParameter = request.getParameter("ack=");
                    String fromParameter = request.getParameter("from=");
                    int ack;
                    int from;
                    if (ackParameter != null && fromParameter != null) {
                        ack = Integer.parseInt(ackParameter);
                        from = Integer.parseInt(fromParameter);
                        if (ack <= 0 || ack > from || from > config.clusterUrls().size()) {
                            ServiceUtils.sendResponse(session, Response.BAD_REQUEST);
                            return;
                        }
                    } else if (ackParameter == null && fromParameter == null) {
                        from = config.clusterUrls().size();
                        ack = quorum(from);
                    } else {
                        ServiceUtils.sendResponse(session, Response.BAD_REQUEST);
                        return;
                    }

                    List<CompletableFuture<ResponseInfo>> futures = new ArrayList<>();
                    List<ResponseInfo> responseInfos = new ArrayList<>();
                    Set<Integer> keyRelatedNodeNumbers = getReplicaNodeNumbersBy(id, from);
                    for (int nodeNumber : keyRelatedNodeNumbers) {
                        // both handle() methods wrapped with try / catch Exception
                        if (nodeNumber == selfNodeNumber) {
                            responseInfos.add(daoHandler.proceed(id, request, requestTime));
                        } else {
                            futures.add(proxyHandler.proceed(request, nodesNumberToUrlMap.get(nodeNumber), requestTime));
                        }
                    }
                    for (CompletableFuture<ResponseInfo> future : futures) {
                        try {
                            responseInfos.add(future.get());
                        } catch (InterruptedException | ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    int[] positiveStatusCodes = new int[0];
                    switch (request.getMethod()) {
                        case Request.METHOD_GET -> {
                            positiveStatusCodes = new int[]{HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_NOT_FOUND};
                        }
                        case Request.METHOD_PUT -> {
                            positiveStatusCodes = new int[]{HttpURLConnection.HTTP_CREATED};
                        }
                        case Request.METHOD_DELETE -> {
                            positiveStatusCodes = new int[]{HttpURLConnection.HTTP_ACCEPTED};
                        }
                    }
                    int counter = 0;
                    long latestRequestTime = 0;
                    ResponseInfo latestRequestTimeInfo = responseInfos.get(0);
                    for (ResponseInfo responseInfo : responseInfos) {
                        for (int statusCode : positiveStatusCodes) {
                            if (responseInfo.httpStatusCode == statusCode) {
                                counter++;
                                break;
                            }
                        }
                         System.out.println(request.getMethod() + "  " +  responseInfo.requestTime + "  " + responseInfo.httpStatusCode);
                        if (responseInfo.requestTime  != null && responseInfo.requestTime > latestRequestTime) {
                            latestRequestTimeInfo = responseInfo;
                            latestRequestTime = responseInfo.requestTime;
                        }
                    }
                    if (counter >= ack) {
                        ServiceUtils.sendResponse(session, ServiceUtils.toResponse(latestRequestTimeInfo));
                    } else {
                        ServiceUtils.sendResponse(session, new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY));
                    }
                }));
            }

            @Override
            public synchronized void stop() {
                for (SelectorThread thread : selectors) {
                    for (Session session : thread.selector) {
                        session.close();
                    }
                }
                selectors = new SelectorThread[0];
                super.stop();
            }
        };
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    private static int quorum(int from) {
        return (from / 2) + 1;
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        try {
            RequestExecutorService.shutdownAndAwaitTermination(requestExecutor);
        } catch (TimeoutException e) {
            LOG.warn("Request executor await termination too long");
        }
        proxyHandler.close();
        server.stop();
        daoHandler.close();
        return CompletableFuture.completedFuture(null);
    }

    private void fillVirtualNodes() {
        int collisionCounter = 1;
        for (String url : config.clusterUrls()) {
            int nodeNumber = config.clusterUrls().indexOf(url);
            for (int i = 0; i < VIRTUAL_NODES_NUMBER; i++) {
                int nodePosition = calculateHashRingPosition(url + i + collisionCounter);
                while (virtualNodes.containsKey(nodePosition)) {
                    nodePosition = calculateHashRingPosition(url + i + collisionCounter++);
                }
                virtualNodes.put(nodePosition, nodeNumber);
            }
        }
    }

    private Set<Integer> getReplicaNodeNumbersBy(String key, int ack) {
        Map.Entry<Integer, Integer> virtualNode = virtualNodes.ceilingEntry(calculateHashRingPosition(key));
        if (virtualNode == null) {
            virtualNode = virtualNodes.firstEntry();
        }
        Set<Integer> replicaNodesPositions = new HashSet<>();
        for (Integer nodePosition : virtualNodes.tailMap(virtualNode.getKey()).values()) {
            replicaNodesPositions.add(nodePosition);
            if (replicaNodesPositions.size() == ack) {
                break;
            }
        }
        if (replicaNodesPositions.size() < ack) {
            for (Integer nodePosition : virtualNodes.values()) {
                replicaNodesPositions.add(nodePosition);
                if (replicaNodesPositions.size() == ack) {
                    break;
                }
            }
        }
        return replicaNodesPositions;
    }

    private int calculateHashRingPosition(String url) {
        return Math.abs(Hash.murmur3(url)) % HASH_SPACE;
    }

    @ServiceFactory(stage = 4, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new DemoService(config);
        }
    }
}
