package ok.dht.test.kovalenko;

import ok.dht.test.kovalenko.dao.utils.PoolKeeper;
import ok.dht.test.kovalenko.utils.HttpUtils;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFormat;

public final class LoadBalancer {

    private static final Logger log = LoggerFactory.getLogger(LoadBalancer.class);
    private final Map<String, Node> nodes = new HashMap<>();

    public void balance(MyServiceBase service, String requestId, Request request, MyHttpSession session)
            throws IOException, ExecutionException, InterruptedException {
        Node responsibleNodeForKey = responsibleNodeForKey(requestId);
        Response response;

        if (responsibleNodeForKey.isIll()) {
            response = MyServiceBase.emptyResponseFor(Response.NOT_FOUND);
            session.sendResponse(response);
            return;
        }

        handleRequest(request, session, service, responsibleNodeForKey);
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

    private int hash(String key, String serviceUrl, int size) {
        return (key + serviceUrl).hashCode() % size;
    }

    private void handleRequest(Request request, MyHttpSession session,
                               MyServiceBase service, Node responsibleNodeForKey) {
        MyServiceBase.Handler finalHandler = service.selfUrl().equals(responsibleNodeForKey.selfUrl())
                ? service::handle
                : responsibleNodeForKey::proxyRequest;
        handleRequestAndSendResponse(request, responsibleNodeForKey, finalHandler, session);
    }

    private void handleRequestAndSendResponse(Request request,
                                              Node responsibleNodeForKey, MyServiceBase.Handler handler, MyHttpSession session) {
        Response response;
        try {
            Object nodedResponse = nodeRequest(request, responsibleNodeForKey, handler, session);
            if (nodedResponse instanceof Response) {
                response = (Response) nodedResponse;
            } else {
                HttpResponse<byte[]> httpResponse = (HttpResponse<byte[]>) nodedResponse;
                response = HttpUtils.toOneNio(httpResponse);
            }
        } catch (Exception e) {
            log.error("Fatal error", e);
            response = MyServiceBase.emptyResponseFor(Response.NOT_FOUND);
        }

        if (response.getStatus() == HttpURLConnection.HTTP_GATEWAY_TIMEOUT) {
            makeNodeIll(responsibleNodeForKey.selfUrl());
            log.error("Node {} is ill", responsibleNodeForKey.selfUrl(), new Exception(Arrays.toString(response.getBody())));
        }

        Response finalResponse = response;
        HttpUtils.NetRequest netRequest = () -> session.sendResponse(finalResponse);
        HttpUtils.safeHttpRequest(session, log, netRequest);
    }

    private Object nodeRequest(Request request, Node responsibleNodeForKey, MyServiceBase.Handler handler, MyHttpSession session) {
        try {
            return handler.handle(request, session);
        } catch (Exception e) {
            makeNodeIll(responsibleNodeForKey.selfUrl());
            log.error("Node {} is ill", responsibleNodeForKey.selfUrl(), e);
            return MyServiceBase.emptyResponseFor(Response.NOT_FOUND);
        }
    }

}
