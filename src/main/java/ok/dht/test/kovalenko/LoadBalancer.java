package ok.dht.test.kovalenko;

import ok.dht.test.kovalenko.utils.HttpUtils;
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
import java.util.concurrent.ExecutionException;

public final class LoadBalancer {

    private static final Logger log = LoggerFactory.getLogger(LoadBalancer.class);
    private final Map<String, Node> nodes = new HashMap<>();

    public void balance(MyServiceBase service, Request request, MyHttpSession session)
            throws IOException, ExecutionException, InterruptedException {
        List<Node> replicasNodeForKey = replicasForKey(session.getRequestId(), session.getReplicas().ack());
        Response response;

        for (Node node : nodes.values()) {
            if (node.isIll()) {
                response = MyServiceBase.emptyResponseFor(HttpUtils.NOT_ENOUGH_REPLICAS);
                session.sendResponse(response);
                return;
            }
         }

        handleWithReplicas(request, session, service, replicasNodeForKey);
    }

    // Rendezvous hashing
    public Node responsibleNodeForKey(String key) {
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("Got empty array of URL's for hashing");
        }

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

    private List<Node> replicasForKey(String key, int ack) {
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("Got empty array of URL's for hashing");
        }

        List<Node> replicasForKey = new ArrayList<>(ack);
        int gotReplicas = 0;
        while (gotReplicas != ack && !nodes.isEmpty()) {
            Node replicaForKey = responsibleNodeForKey(key);
            replicasForKey.add(replicaForKey);
            nodes.remove(replicaForKey.selfUrl());
            ++gotReplicas;
        }
        replicasForKey.forEach(r -> nodes.put(r.selfUrl(), r));
        return replicasForKey;
    }

    private int hash(String key, String serviceUrl, int size) {
        return (key + serviceUrl).hashCode() % size;
    }

    private void handleWithReplicas(Request request, MyHttpSession session,
                               MyServiceBase service, List<Node> replicasForKey) {
        Response response;
        try {
            request.addHeader("replica");
            int nApproves = 0;
            List<Response> replicaGoodResponses = new ArrayList<>(replicasForKey.size());
            for (Node replicaForKey : replicasForKey) {
                Response replicaResponse;
                if (replicaForKey.selfUrl().equals(service.selfUrl())) {
                    replicaResponse = nodeRequest(request, service::handle, session);
                } else {
                    replicaResponse = nodeRequest(request, replicaForKey::proxyRequest, session);
                }

                if (isGoodResponse(replicaResponse)) {
                    ++nApproves;
                    replicaGoodResponses.add(replicaResponse);
                } else {
                    makeNodeIll(replicaForKey.selfUrl());
                    log.error("Node {} is ill", replicaForKey.selfUrl(), new Exception(Arrays.toString(replicaResponse.getBody())));
                }
            }

            if (nApproves == session.getReplicas().ack()) {
                response = replicaGoodResponses.get(0);
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

    private Response nodeRequest(Request request, MyServiceBase.Handler handler, MyHttpSession session) {
        try {
            Object nodedResponse =  handler.handle(request, session);
            if (nodedResponse instanceof Response) {
                return (Response) nodedResponse;
            } else {
                HttpResponse<byte[]> httpResponse = (HttpResponse<byte[]>) nodedResponse;
                return HttpUtils.toOneNio(httpResponse);
            }
        } catch (Exception e) {
            return MyServiceBase.emptyResponseFor(Response.INTERNAL_ERROR);
        }
    }

    // FIXME explicit http-codes
    private boolean isGoodResponse(Response response) {
        return response.getStatus() == HttpURLConnection.HTTP_OK
                || response.getStatus() == HttpURLConnection.HTTP_CREATED
                || response.getStatus() == HttpURLConnection.HTTP_ACCEPTED
                || response.getStatus() == HttpURLConnection.HTTP_NOT_FOUND;
    }

}
