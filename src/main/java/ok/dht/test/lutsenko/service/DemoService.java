package ok.dht.test.lutsenko.service;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.lutsenko.dao.common.BaseEntry;
import ok.dht.test.lutsenko.dao.common.DaoConfig;
import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.server.SelectorThread;
import one.nio.util.Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

public class DemoService implements Service {

    private static final int MAX_NODES_NUMBER = 1000; // was limited in lection
    private static final int VIRTUAL_NODES_NUMBER = 10;
    private static final int HASH_SPACE = MAX_NODES_NUMBER * VIRTUAL_NODES_NUMBER * 360;
    private static final String DAO_PREFIX = "dao";
    private static final Logger LOG = LoggerFactory.getLogger(DemoService.class);

    private final Path daoPath;
    private final ServiceConfig config;
    private final int selfNodeNumber;
    private final Map<Integer, String> nodesNumberToUrlMap = new HashMap<>();
    private final NavigableMap<Integer, Integer> virtualNodes = new TreeMap<>(); // node position to node number

    //--------------------------Non final fields due to stop / close / shutdown in stop()--------------------------\\
    private HttpServer server;
    private ExecutorService requestExecutor;
    private DaoHandler daoHandler;
    private ProxyHandler proxyHandler;
    //--------------------------------------------------------------------------------------------------------------\\

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
            public HttpSession createSession(Socket socket) {
                return new ExtendedSession(socket, this);
            }

            @Override
            public void handleRequest(Request request, HttpSession session) {
                long requestTime = System.currentTimeMillis();
                requestExecutor.execute(new SessionRunnable(session, () -> {
                    if (isProxyRequestAndHandle(request, session)) {
                        return;
                    }
                    switch (request.getPath()) {
                        case "/v0/entity" -> RequestParser.parse(request)
                                .checkSuccessStatusCodes()
                                .checkId()
                                .checkAckFrom(config.clusterUrls().size())
                                .onSuccess(requestParser -> {
                                    List<CompletableFuture<Response>> replicaResponsesFutures
                                            = createReplicaResponsesFutures(requestParser, requestTime);
                                    ReplicaResponsesHandler.handle(session, requestParser, replicaResponsesFutures);
                                })
                                .onFail(rp -> ServiceUtils.sendResponse(session, rp.failStatus()));
                        case "/v0/entities" -> RequestParser.parse(request)
                                .checkSuccessStatusCodes()
                                .checkStart()
                                .checkEnd()
                                .onSuccess(requestParser -> {
                                    String start = requestParser.getParam(RequestParser.START_PARAM_NAME).asString();
                                    String end = requestParser.getParam(RequestParser.END_PARAM_NAME).asString();
                                    Iterator<BaseEntry<String>> entriesIterator = daoHandler.getDao().get(start, end);
                                    RangeRequestHandler.handle(ExtendedSession.of(session), entriesIterator);
                                })
                                .onFail(rp -> ServiceUtils.sendResponse(session, rp.failStatus()));
                        default -> ServiceUtils.sendResponse(session, Response.BAD_REQUEST);
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

    @Override
    public CompletableFuture<?> stop() throws IOException {
        try {
            RequestExecutorService.shutdownAndAwaitTermination(requestExecutor);
        } catch (TimeoutException e) {
            LOG.warn("Executor await termination too long", e);
        }
        proxyHandler.close();
        server.stop();
        daoHandler.close();
        return CompletableFuture.completedFuture(null);
    }

    private boolean isProxyRequestAndHandle(Request request, HttpSession session) {
        String proxyRequestTimeHeaderValue = request.getHeader(CustomHeaders.PROXY_REQUEST_TIME);
        if (proxyRequestTimeHeaderValue == null) {
            return false;
        }
        long requestTime = Long.parseLong(proxyRequestTimeHeaderValue);
        daoHandler.handle(request, session, request.getParameter(RequestParser.ID_PARAM_NAME), requestTime);
        return true;
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

    private List<CompletableFuture<Response>> createReplicaResponsesFutures(RequestParser requestParser,
                                                                            long requestTime) {
        Request request = requestParser.getRequest();
        String id = requestParser.getParam(RequestParser.ID_PARAM_NAME).asString();
        int from = requestParser.getParam(RequestParser.FROM_PARAM_NAME).asInt();
        List<CompletableFuture<Response>> replicaResponsesFutures = new ArrayList<>(from);
        for (int nodeNumber : getReplicaNodeNumbers(id, from)) {
            replicaResponsesFutures.add(nodeNumber == selfNodeNumber
                    ? daoHandler.proceed(id, request, requestTime)
                    : proxyHandler.proceed(request, nodesNumberToUrlMap.get(nodeNumber), requestTime)
            );
        }
        return replicaResponsesFutures;
    }

    private Set<Integer> getReplicaNodeNumbers(String key, int from) {
        Map.Entry<Integer, Integer> virtualNode = virtualNodes.ceilingEntry(calculateHashRingPosition(key));
        if (virtualNode == null) {
            virtualNode = virtualNodes.firstEntry();
        }
        Set<Integer> replicaNodesPositions = new HashSet<>(2 * from);
        Collection<Integer> nextVirtualNodesPositions = virtualNodes.tailMap(virtualNode.getKey()).values();
        addReplicaNodePositions(replicaNodesPositions, nextVirtualNodesPositions, from);
        if (replicaNodesPositions.size() < from) {
            addReplicaNodePositions(replicaNodesPositions, virtualNodes.values(), from);
        }
        if (replicaNodesPositions.size() == from) {
            return replicaNodesPositions;
        }
        throw new RuntimeException("Can`t find from amount of replica nodes positions");
    }

    private static void addReplicaNodePositions(Set<Integer> replicaNodesPositions,
                                                Collection<Integer> virtualNodesPositions,
                                                int from) {
        for (Integer nodePosition : virtualNodesPositions) {
            replicaNodesPositions.add(nodePosition);
            if (replicaNodesPositions.size() == from) {
                break;
            }
        }
    }

    private int calculateHashRingPosition(String url) {
        return Math.abs(Hash.murmur3(url)) % HASH_SPACE;
    }

    @ServiceFactory(stage = 6, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new DemoService(config);
        }
    }
}
