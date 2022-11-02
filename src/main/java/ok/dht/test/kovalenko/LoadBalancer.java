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
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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

        switch (request.getMethod()) {
            case Request.METHOD_GET ->
                    handleRequest(requestId, request, session, service::handleGet, service, responsibleNodeForKey);
            case Request.METHOD_PUT ->
                    handleRequest(requestId, request, session, service::handlePut, service, responsibleNodeForKey);
            case Request.METHOD_DELETE ->
                    handleRequest(requestId, request, session, service::handleDelete, service, responsibleNodeForKey);
            default -> throw new IllegalArgumentException("Unexpected request method to be balanced: "
                    + request.getMethod());
        }
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

    private int hash(String key, String serviceUrl, int size) {
        return (key + serviceUrl).hashCode() % size;
    }

    private void handleRequest(String id, Request request, MyHttpSession session, Handler handler,
                               MyServiceBase service, Node responsibleNodeForKey) throws IOException {
        Handler finalHandler = service.selfUrl().equals(responsibleNodeForKey.selfUrl())
                ? handler
                : responsibleNodeForKey::proxyRequest;
        handleRequestAndSendResponse(id, request, responsibleNodeForKey, finalHandler, session);
    }

    private void handleRequestAndSendResponse(String id, Request request,
                                              Node responsibleNodeForKey, Handler handler, MyHttpSession session) {
        Response response;
        try {
            Object nodedResponse = nodeRequest(id, request, responsibleNodeForKey, handler, session);
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
        Response finalResponse = response;
        HttpUtils.NetRequest netRequest = () -> session.sendResponse(finalResponse);
        HttpUtils.safeHttpRequest(session, log, netRequest);
    }

    private Object nodeRequest(String id, Request request, Node node, Handler handler, MyHttpSession session) {
        try {
            return handler.handle(id, request, session);
        } catch (ConnectException e) {
            return MyServiceBase.emptyResponseFor(Response.GATEWAY_TIMEOUT);
        } catch (Exception e) {
            node.setIll(true);
            log.error("Node {} is ill", node.selfUrl(), e);
            return MyServiceBase.emptyResponseFor(Response.NOT_FOUND);
        }
    }

    private interface Handler {
        Object handle(String id, Request request, MyHttpSession session)
                throws IOException, ExecutionException, InterruptedException, IllegalAccessException;
    }

}
