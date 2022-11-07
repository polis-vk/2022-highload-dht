package ok.dht.test.kovalenko;

import ok.dht.test.kovalenko.utils.HttpUtils;
import ok.dht.test.kovalenko.utils.MyHttpSession;
import ok.dht.test.kovalenko.utils.MyOneNioResponse;
import ok.dht.test.kovalenko.utils.ResponseComparator;
import one.nio.http.Request;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ExecutionException;

public final class LoadBalancer {

    private static final Logger log = LoggerFactory.getLogger(LoadBalancer.class);
    private final Map<String, Node> nodes = new HashMap<>();

    public void balance(MyServiceBase service, Request request, MyHttpSession session)
            throws IOException, ExecutionException, InterruptedException {
        makeAllNodesHealthy(); // parody on CircuitBreaker
        int ack = session.getReplicas().ack();
        MyOneNioResponse response;
        if (nodes.size() < ack) {
            response = MyServiceBase.emptyResponseFor(HttpUtils.NOT_ENOUGH_REPLICAS);
            session.sendResponse(response);
            return;
        }

        List<Node> replicasNodeForKey = replicasForKey(session.getRequestId(), ack);

        if (replicasNodeForKey.size() != session.getReplicas().ack()) {
            response = MyServiceBase.emptyResponseFor(HttpUtils.NOT_ENOUGH_REPLICAS);
            session.sendResponse(response);
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
        MyOneNioResponse response;
        try {
            request.addHeader("Replica");
            int numApproves = 0;
            Queue<MyOneNioResponse> replicaGoodResponses = new PriorityQueue<>(ResponseComparator.INSTANSE);
            boolean wasSelfSaving = false;
            for (Node replicaForKey : replicasForKey) {
                MyServiceBase.Handler handler;
                if (replicaForKey.selfUrl().equals(service.selfUrl())) {
                    handler = service::handle;
                    wasSelfSaving = true;
                } else {
                    handler = replicaForKey::proxyRequest;
                }

                numApproves
                        += requestForReplica(request, session, service.selfUrl(), handler, replicaGoodResponses)
                        ? 1
                        : 0;
            }

            if (numApproves != session.getReplicas().ack() && !wasSelfSaving) {
                numApproves
                        += requestForReplica(request, session, service.selfUrl(), service::handle, replicaGoodResponses)
                        ? 1
                        : 0;
            }

            if (numApproves == session.getReplicas().ack()) {
                response = replicaGoodResponses.peek();
            } else {
                response = MyServiceBase.emptyResponseFor(HttpUtils.NOT_ENOUGH_REPLICAS);
            }
        } catch (Exception e) {
            log.error("Fatal error", e);
            response = MyServiceBase.emptyResponseFor(Response.INTERNAL_ERROR);
        }

        Response finalResponse = response;
        HttpUtils.safeHttpRequest(session, log, () -> session.sendResponse(finalResponse));
    }

    private boolean requestForReplica(Request request, MyHttpSession session, String url,
                                      MyServiceBase.Handler handler, Queue<MyOneNioResponse> replicaGoodResponses) {
        MyOneNioResponse replicaResponse = nodeRequest(request, handler, session);
        if (isGoodResponse(replicaResponse)) {
            replicaGoodResponses.add(replicaResponse);
            return true;
        } else {
            makeNodeIll(url);
            log.error("Node {} is ill", url, new Exception(Arrays.toString(replicaResponse.getBody())));
            return false;
        }
    }

    private MyOneNioResponse nodeRequest(Request request, MyServiceBase.Handler handler, MyHttpSession session) {
        try {
            Object nodedResponse = handler.handle(request, session);
            if (nodedResponse instanceof MyOneNioResponse) {
                return (MyOneNioResponse) nodedResponse;
            } else {
                HttpResponse<byte[]> httpResponse = (HttpResponse<byte[]>) nodedResponse;
                return HttpUtils.toOneNio(httpResponse);
            }
        } catch (Exception e) {
            return MyServiceBase.emptyResponseFor(Response.INTERNAL_ERROR);
        }
    }

    private boolean isGoodResponse(Response response) {
        return response.getStatus() == HttpURLConnection.HTTP_OK
                || response.getStatus() == HttpURLConnection.HTTP_CREATED
                || response.getStatus() == HttpURLConnection.HTTP_ACCEPTED
                || response.getStatus() == HttpURLConnection.HTTP_NOT_FOUND;
    }

}
