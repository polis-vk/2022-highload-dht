package ok.dht.test.lutsenko.service;

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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class DemoService implements Service {

    private static final int MAX_NODES_NUMBER = 1000; // was limited in lection
    private static final int VIRTUAL_NODES_NUMBER = 10;
    private static final int HASH_SPACE = MAX_NODES_NUMBER * VIRTUAL_NODES_NUMBER * 360;
    private static final String DAO_PREFIX = "dao";
    private static final String NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";
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
            public void handleRequest(Request request, HttpSession session) {
                long requestTime = System.currentTimeMillis();
                requestExecutor.execute(new SessionRunnable(session, () -> {
                    if (isProxyRequestAndHandle(request, session)) {
                        return;
                    }
                    RequestParser requestParser = new RequestParser(request)
                            .checkPath()
                            .checkSuccessStatusCodes()
                            .checkId()
                            .checkAckFrom(config.clusterUrls().size());
                    if (requestParser.isFailed()) {
                        ServiceUtils.sendResponse(session, requestParser.failStatus());
                        return;
                    }
                    List<CompletableFuture<Response>> replicaResponsesFutures = createReplicaResponsesFutures(
                            requestParser,
                            requestTime
                    );
                    handleReplicaResponses(session, requestParser, replicaResponsesFutures);
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

    private List<CompletableFuture<Response>> createReplicaResponsesFutures(RequestParser requestParser,
                                                                            long requestTime) {
        String id = requestParser.id();
        Request request = requestParser.getRequest();
        List<CompletableFuture<Response>> replicaResponsesFutures = new ArrayList<>(requestParser.from());
        for (int nodeNumber : getReplicaNodeNumbers(id, requestParser.from())) {
            replicaResponsesFutures.add(nodeNumber == selfNodeNumber
                    ? CompletableFuture.completedFuture(daoHandler.proceed(id, request, requestTime))
                    : proxyHandler.proceed(request, nodesNumberToUrlMap.get(nodeNumber), requestTime)
            );
        }
        return replicaResponsesFutures;
    }

    private void handleReplicaResponses(HttpSession session,
                                        RequestParser requestParser,
                                        List<CompletableFuture<Response>> replicaResponsesFutures) {
        // Может возникнуть вопрос зачем нужны счетчики, ведь можно использовать размер мапы responses?
        // Чтобы не класть в мапу лишние значения, если requestTime не важен, например при PUT и DELETE.
        // Также при GET запросах с помощью CustomHeaders.REQUEST_TIME различается ситуации когда ключ не найден, тогда
        // requestTime невозможно записать, так как в dao его нет и в мапу такие ответы не кладутся, но successCounter
        // увеличивается. Когда найдена могила, то requestTime указывается и значение в мапу добавляется.
        // Если мапа пустая и CustomHeaders.REQUEST_TIME отсутствует, то requestTime принимаем за 0 и делаем одну
        // запись в мапу. Также счетчики позволяют немедленно выполнить запросы как Continue к другим репликам если
        // кворум уже набран или количество отказов гарантированно не позволит его собрать и эти задачи еще не начались.
        int ack = requestParser.ack();
        int failLimit = requestParser.from() - ack + 1;
        AtomicInteger failsCounter = new AtomicInteger(0);
        AtomicInteger successCounter = new AtomicInteger(0);
        NavigableMap<Long, Response> responses = new ConcurrentSkipListMap<>();
        for (CompletableFuture<Response> responseFuture : replicaResponsesFutures) {
            responseFuture.thenAccept(response -> {
                if (response.getStatus() == 100) { // Response.CONTINUE
                    return;
                }
                if (requestParser.successStatuses().contains(response.getStatus())) {
                    String requestTimeHeaderValue = response.getHeader(CustomHeaders.REQUEST_TIME);
                    if (requestTimeHeaderValue == null && responses.isEmpty()) {
                        responses.put(0L, response);
                    } else if (requestTimeHeaderValue != null) { // code climate ругается, на != в первом if
                        responses.put(Long.parseLong(requestTimeHeaderValue), response);
                    }
                    if (successCounter.incrementAndGet() == ack) {
                        ServiceUtils.sendResponse(session, responses.lastEntry().getValue());
                        completeAsContinueNonDoneFutures(replicaResponsesFutures);
                    }
                } else if (failsCounter.incrementAndGet() == failLimit) {
                    ServiceUtils.sendResponse(session, NOT_ENOUGH_REPLICAS);
                    completeAsContinueNonDoneFutures(replicaResponsesFutures);
                }
            }).exceptionally(throwable -> {
                if (failsCounter.incrementAndGet() == failLimit) {
                    ServiceUtils.sendResponse(session, NOT_ENOUGH_REPLICAS);
                    completeAsContinueNonDoneFutures(replicaResponsesFutures);
                }
                LOG.error("Getting response from replica failed", throwable);
                return null;
            });
        }
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

    private Set<Integer> getReplicaNodeNumbers(String key, int from) {
        Map.Entry<Integer, Integer> virtualNode = virtualNodes.ceilingEntry(calculateHashRingPosition(key));
        if (virtualNode == null) {
            virtualNode = virtualNodes.firstEntry();
        }
        Set<Integer> replicaNodesPositions = new HashSet<>(from);
        Collection<Integer> nextVirtualNodesPositions = virtualNodes.tailMap(virtualNode.getKey()).values();
        addReplicaNodePositions(replicaNodesPositions, nextVirtualNodesPositions, from);
        if (replicaNodesPositions.size() < from) {
            addReplicaNodePositions(replicaNodesPositions, virtualNodes.values(), from);
        }
        if (replicaNodesPositions.size() == from) {
            return replicaNodesPositions;
        }
        throw new IllegalArgumentException("Can`t find from amount of replica nodes positions");
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

    private void completeAsContinueNonDoneFutures(List<CompletableFuture<Response>> replicaResponsesFutures) {
        for (CompletableFuture<Response> responseFuture : replicaResponsesFutures) {
            if (responseFuture != null && !responseFuture.isDone()) {
                responseFuture.complete(new Response(Response.CONTINUE, Response.EMPTY));
            }
        }
    }

    @ServiceFactory(stage = 5, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new DemoService(config);
        }
    }
}
