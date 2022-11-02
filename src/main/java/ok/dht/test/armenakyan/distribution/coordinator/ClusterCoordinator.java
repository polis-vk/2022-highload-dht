package ok.dht.test.armenakyan.distribution.coordinator;

import ok.dht.ServiceConfig;
import ok.dht.test.armenakyan.distribution.ProxyNodeHandler;
import ok.dht.test.armenakyan.distribution.SelfNodeHandler;
import ok.dht.test.armenakyan.distribution.hashing.ConsistentHashing;
import ok.dht.test.armenakyan.distribution.hashing.KeyHasher;
import ok.dht.test.armenakyan.distribution.model.Node;
import one.nio.http.HttpSession;
import one.nio.http.Request;

import java.io.Closeable;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        AcquireContext acquireContext = new AcquireContext(ack, from, session);

        for (Node node : consistentHashing.nodesByKey(key, from)) {
            node.requestHandler()
                    .handleForKeyAsync(key, request, timestamp)
                    .thenAccept(acquireContext::acquireResponse);
        }
    }

    @Override
    public void close() throws IOException {
        clientPool.shutdownNow();
        for (Node node : nodes) {
            node.requestHandler().close();
        }
    }

}
