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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
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
                requestExecutor.execute(new SessionRunnable(session, () -> {
                    String id = request.getParameter("id=");
                    if (id == null || id.isBlank()) {
                        ServiceUtils.sendResponse(session, Response.BAD_REQUEST);
                        return;
                    }
                    int keyLocationNodeNumber = getNodeNumberBy(id);
                    // both handle() methods wrapped with try / catch Exception
                    if (keyLocationNodeNumber == selfNodeNumber) {
                        daoHandler.handle(id, request, session);
                    } else {
                        proxyHandler.handle(request, session, nodesNumberToUrlMap.get(keyLocationNodeNumber));
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

    private int getNodeNumberBy(String key) {
        Map.Entry<Integer, Integer> virtualNode = virtualNodes.ceilingEntry(calculateHashRingPosition(key));
        return (virtualNode == null ? virtualNodes.firstEntry() : virtualNode).getValue();
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
