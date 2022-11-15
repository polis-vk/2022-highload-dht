package ok.dht.test.kovalenko;

import ok.dht.test.kovalenko.utils.CompletableFutureSubscriber;
import ok.dht.test.kovalenko.utils.CompletableFutureUtils;
import ok.dht.test.kovalenko.utils.HttpUtils;
import ok.dht.test.kovalenko.utils.MyHttpResponse;
import ok.dht.test.kovalenko.utils.MyHttpSession;
import ok.dht.test.kovalenko.utils.ResponseComparator;
import one.nio.http.Request;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class LoadBalancer {

    private static final Logger log = LoggerFactory.getLogger(LoadBalancer.class);
    private final Map<String, Node> nodes = new HashMap<>();
    private final CompletableFutureSubscriber completableFutureSubscriber = new CompletableFutureSubscriber();

    public void balance(MyServiceBase service, Request request, MyHttpSession session)
            throws IOException, ExecutionException, InterruptedException {
        int from = session.getReplicas().from();
        if (nodes.size() < from) {
            session.sendResponse(MyHttpResponse.notEnoughReplicas());
            return;
        }

        List<Node> replicas = replicasFor(session.getRequestId(), from);
        handleWithReplicas(request, session, service, replicas);
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

    private List<Node> replicasFor(String key, int from) {
        List<Node> replicasForKey = new ArrayList<>(from);
        for (int i = 0; i < from; ++i) {
            Node replicaForKey = responsibleNodeForKey(key, nodes);
            nodes.remove(replicaForKey.selfUrl());
            replicasForKey.add(replicaForKey);
        }
        replicasForKey.forEach(r -> nodes.put(r.selfUrl(), r));
        return replicasForKey;
    }

    private int hash(String key, String serviceUrl, int size) {
        return (key + serviceUrl).hashCode() % size;
    }

    private void handleWithReplicas(Request request, MyHttpSession session,
                                    MyServiceBase service, List<Node> replicas) {
        try {
            request.addHeader(HttpUtils.REPLICA_HEADER);
            PriorityBlockingQueue<MyHttpResponse> replicasGoodResponses
                    = new PriorityBlockingQueue<>(session.getReplicas().from(), ResponseComparator.INSTANSE);
            PriorityBlockingQueue<MyHttpResponse> replicasBadResponses
                    = new PriorityBlockingQueue<>(session.getReplicas().from(), ResponseComparator.INSTANSE);
            AtomicInteger acks = new AtomicInteger();
            AtomicBoolean responseSent = new AtomicBoolean();
            String masterNodeUrl = service.selfUrl();

            for (Node replica : replicas) {
                if (responseSent.get()) {
                    break;
                }

                MyServiceBase.Handler handler;
                String slaveNodeUrl = replica.selfUrl();
                if (slaveNodeUrl.equals(masterNodeUrl)) {
                    handler = service::handleEntity;
                } else {
                    handler = replica::proxyRequest;
                }

                CompletableFuture<?> cf = handler.handle(request, session);

                CompletableFutureUtils.Subscription base
                        = new CompletableFutureUtils.Subscription(cf, session, this, slaveNodeUrl);
                CompletableFutureUtils.ExtendedSubscription extendedSubscription =
                        new CompletableFutureUtils.ExtendedSubscription(base,
                                acks, responseSent, replicasGoodResponses, replicasBadResponses);
                completableFutureSubscriber.aggregate(extendedSubscription);
            }
        } catch (Exception e) {
            log.error("Fatal error", e);
            MyHttpResponse finalResponse = new MyHttpResponse(Response.INTERNAL_ERROR);
            HttpUtils.safeHttpRequest(session, log, () -> session.sendResponse(finalResponse));
        }
    }
}
