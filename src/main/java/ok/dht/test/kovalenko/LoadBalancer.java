package ok.dht.test.kovalenko;

import ok.dht.test.kovalenko.utils.CompletableFutureSubscriber;
import ok.dht.test.kovalenko.utils.HttpUtils;
import ok.dht.test.kovalenko.utils.MyHttpResponse;
import ok.dht.test.kovalenko.utils.MyHttpSession;
import ok.dht.test.kovalenko.utils.ResponseComparator;
import one.nio.http.Request;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public final class LoadBalancer {

    private static final Logger log = LoggerFactory.getLogger(LoadBalancer.class);
    private final Map<String, Node> nodes = new HashMap<>();
    private final CompletableFutureSubscriber completableFutureSubscriber;

    public LoadBalancer() {
        this.completableFutureSubscriber = new CompletableFutureSubscriber(this);
    }

    public void balance(MyServiceBase service, Request request, MyHttpSession session)
            throws IOException, ExecutionException, InterruptedException {
        makeAllNodesHealthy(); // parody on CircuitBreaker
        int ack = session.getReplicas().ack();
        int from = session.getReplicas().from();
        if (nodes.size() < ack) {
            session.sendResponse(MyHttpResponse.notEnoughReplicas());
            return;
        }

        List<Node> replicasNodeForKey = replicasForKey(session.getRequestId(), from);

        if (replicasNodeForKey.size() != from) {
            session.sendResponse(MyHttpResponse.notEnoughReplicas());
            return;
        }

        handleWithReplicas(request, session, service, replicasNodeForKey);
    }

    // Rendezvous hashing
    public Node responsibleNodeForKey(String key, Map<String, Node> nodes) {
        int maxHash = Integer.MIN_VALUE;
        String maxHashUrl = null;
        for (String url : nodes.keySet()) {
            int hash = hash(key, url, nodes.size());
            if (hash >= maxHash) {
                maxHash = hash;
                maxHashUrl = url;
            }
        }
        return nodes.get(maxHashUrl);
    }

    public void add(MyServiceBase service) {
        service.clusterUrls().forEach(url -> nodes.put(url, new Node(url)));
    }

    public void remove(MyServiceBase service) {
        service.clusterUrls().forEach(nodes::remove);
    }

    public void makeNodeIll(String nodeUrl) {
        nodes.get(nodeUrl).setIll(true);
    }

    public void makeAllNodesHealthy() {
        for (Node node : nodes.values()) {
            node.setIll(false);
        }
    }

    private List<Node> replicasForKey(String key, int ack) {
        List<Node> replicasForKey = new ArrayList<>(ack);
        int gotReplicas = 0;
        while (gotReplicas != ack && !nodes.isEmpty()) {
            Node replicaForKey = responsibleNodeForKey(key, nodes);
            nodes.remove(replicaForKey.selfUrl());
            if (!replicaForKey.isIll()) {
                replicasForKey.add(replicaForKey);
                ++gotReplicas;
            }
        }
        replicasForKey.forEach(r -> nodes.put(r.selfUrl(), r));
        return replicasForKey;
    }

    private int hash(String key, String serviceUrl, int size) {
        return (key + serviceUrl).hashCode() % size;
    }

    private void handleWithReplicas(Request request, MyHttpSession session,
                                    MyServiceBase service, List<Node> replicasForKey) {
        try {
            request.addHeader(HttpUtils.REPLICA_HEADER);
            PriorityBlockingQueue<MyHttpResponse> replicasGoodResponses = new PriorityBlockingQueue<>(session.getReplicas().from(), ResponseComparator.INSTANSE);
            PriorityBlockingQueue<MyHttpResponse> replicasBadResponses = new PriorityBlockingQueue<>(session.getReplicas().from(), ResponseComparator.INSTANSE);
            AtomicInteger acks = new AtomicInteger();
            String masterNodeUrl = service.selfUrl();

            for (Node replicaForKey : replicasForKey) {
                MyServiceBase.Handler handler;
                String slaveNodeUrl = replicaForKey.selfUrl();
                if (slaveNodeUrl.equals(masterNodeUrl)) {
                    handler = service::handle;
                } else {
                    handler = replicaForKey::proxyRequest;
                }

                requestForReplica(request, session, slaveNodeUrl, handler, acks, replicasGoodResponses, replicasBadResponses);
            }
        } catch (Exception e) {
            log.error("Fatal error", e);
            MyHttpResponse finalResponse = new MyHttpResponse(Response.INTERNAL_ERROR);
            HttpUtils.safeHttpRequest(session, log, () -> session.sendResponse(finalResponse));
        }
    }

    private void requestForReplica(Request request, MyHttpSession session, String slaveNodeUrl, MyServiceBase.Handler handler, AtomicInteger acks,
                                   PriorityBlockingQueue<MyHttpResponse> replicasGoodResponses, PriorityBlockingQueue<MyHttpResponse> replicasBadResponses)
            throws IOException, ExecutionException, InterruptedException, InvocationTargetException, IllegalAccessException {
        CompletableFuture<?> completableFuture = handler.handle(request, session);
        completableFutureSubscriber.subscribe(completableFuture, session, slaveNodeUrl, acks, replicasGoodResponses, replicasBadResponses);
    }

}
