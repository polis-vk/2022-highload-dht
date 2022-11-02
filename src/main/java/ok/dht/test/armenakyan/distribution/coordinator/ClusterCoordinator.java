package ok.dht.test.armenakyan.distribution.coordinator;

import ok.dht.ServiceConfig;
import ok.dht.test.armenakyan.distribution.ProxyNodeHandler;
import ok.dht.test.armenakyan.distribution.SelfNodeHandler;
import ok.dht.test.armenakyan.distribution.hashing.ConsistentHashing;
import ok.dht.test.armenakyan.distribution.hashing.KeyHasher;
import ok.dht.test.armenakyan.distribution.model.Node;
import ok.dht.test.armenakyan.distribution.model.Value;
import ok.dht.test.armenakyan.util.ServiceUtils;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

import java.io.Closeable;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ClusterCoordinator implements Closeable {
    private static final int CLIENT_WORKERS = Runtime.getRuntime().availableProcessors();
    private static final int MAX_TASKS_PER_NODE = 1000;
    private final ConsistentHashing consistentHashing;
    private final List<Node> nodes;
    private final SelfNodeHandler daoHandler;
    private final ExecutorService clientPool;
    public ClusterCoordinator(ServiceConfig config,
                              KeyHasher keyHasher,
                              ExecutorService internalPool) throws IOException {
        this.clientPool = Executors.newFixedThreadPool(CLIENT_WORKERS);
        this.daoHandler = new SelfNodeHandler(config.workingDir(), internalPool);
        this.nodes = initializeNodes(config.selfUrl(), config.clusterUrls());
        this.consistentHashing = new ConsistentHashing(nodes, keyHasher);
    }

    private List<Node> initializeNodes(String selfUrl, List<String> clusterUrls) {
        List<Node> nodeList = new ArrayList<>();
        nodeList.add(new Node(selfUrl, daoHandler));

        HttpClient client = HttpClient.newBuilder().executor(clientPool).build();

        for (String nodeUrl : clusterUrls) {
            if (nodeUrl.equals(selfUrl)) {
                continue;
            }

            nodeList.add(new Node(nodeUrl, new ProxyNodeHandler(nodeUrl, client, MAX_TASKS_PER_NODE)));
        }

        return nodeList;
    }

    public void handleLocally(String key, Request request, HttpSession session) throws IOException {
        daoHandler.handleForKey(key, request, session, -1);
    }

    public void handle(String key, Request request, HttpSession session) throws IOException {
        long timestamp = System.currentTimeMillis();
        consistentHashing.nodeByKey(key)
                .requestHandler()
                .handleForKey(key, request, session, timestamp);
    }

    public void replicate(
            String key,
            Request request,
            HttpSession session,
            int ack,
            int from
    ) {
        long timestamp = System.currentTimeMillis();

        AcquireContext acquireContext = new AcquireContext(ack, request);

        for (Node node : consistentHashing.nodesByKey(key, from)) {
            node.requestHandler()
                    .handleForKeyAsync(key, request, timestamp)
                    .thenAccept(response -> {
                        acquireContext.responses.add(response);
                        int latch = acquireContext.ackLatch().decrementAndGet();
                        if (latch == 0) {
                            try {
                                session.sendResponse(mergeResponses(acquireContext));
                            } catch (Exception e) {
                                session.close();
                            }
                        }
                    });
        }
    }

    private Response mergeResponses(AcquireContext acquireContext) {
        Map<Integer, Value> codeToValue = new HashMap<>();
        Map<Integer, Integer> codeCount = new HashMap<>();

        for (Response response : acquireContext.responses()) {
            if (response.getBody() != null && response.getBody().length != 0) {
                codeToValue.merge(
                        response.getStatus(),
                        Value.fromBytes(response.getBody()),
                        ClusterCoordinator::latestValue
                );
            }
            codeCount.merge(
                    response.getStatus(),
                    1,
                    Integer::sum
            );
        }
        int ackNumber = acquireContext.ackNumber();

        switch (acquireContext.request().getMethod()) {
            case Request.METHOD_GET -> {
                Value okValue = codeToValue.get(200);
                int okCount = codeCount.getOrDefault(200, 0);
                if (okCount >= ackNumber) {
                    return new Response(Response.OK, okValue.value());
                }

                int notFoundCount = codeCount.getOrDefault(404, 0);
                if (notFoundCount + okCount >= ackNumber) {
                    Value latestValue = latestValue(codeToValue.get(404), okValue);

                    if (latestValue != null && !latestValue.isTombstone()) {
                        return new Response(Response.OK, latestValue.value());
                    } else {
                        return new Response(Response.NOT_FOUND, Response.EMPTY);
                    }
                }
            }
            case Request.METHOD_PUT -> {
                if (codeCount.getOrDefault(201, 0) >= ackNumber) {
                    return new Response(Response.CREATED, Response.EMPTY);
                }
            }
            case Request.METHOD_DELETE -> {
                if (codeCount.getOrDefault(202, 0) >= ackNumber) {
                    return new Response(Response.ACCEPTED, Response.EMPTY);
                }
            }
            default -> {
                return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            }
        }
        return new Response(ServiceUtils.NOT_ENOUGH_REPLICAS, Response.EMPTY);
    }

    private static Value latestValue(Value first, Value second) {
        if (second == null) {
            return first;
        }
        if (first == null || first.timestamp() < second.timestamp()) {
            return second;
        }
        if (first.timestamp() == second.timestamp()) {
            return first.isTombstone() ? second : first;
        }

        return first;
    }

    @Override
    public void close() throws IOException {
        clientPool.shutdownNow();
        for (Node node : nodes) {
            node.requestHandler().close();
        }
    }

    private static final class AcquireContext {
        private final int ackNumber;
        private final Request request;
        private final AtomicInteger ackLatch;
        private final ConcurrentLinkedDeque<Response> responses;

        private AcquireContext(int ack, Request request) {
            this.ackNumber = ack;
            this.ackLatch = new AtomicInteger(ack);
            this.request = request;
            this.responses = new ConcurrentLinkedDeque<>();
        }

        public int ackNumber() {
            return ackNumber;
        }

        public AtomicInteger ackLatch() {
            return ackLatch;
        }

        public ConcurrentLinkedDeque<Response> responses() {
            return responses;
        }

        public Request request() {
            return request;
        }
    }
}
