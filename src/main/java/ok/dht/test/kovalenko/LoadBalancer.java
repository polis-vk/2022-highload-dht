package ok.dht.test.kovalenko;

import ok.dht.test.kovalenko.utils.HashUtils;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class LoadBalancer {

    private static final Map<String, Node> nodes = new HashMap<>();
    private static final Logger log = LoggerFactory.getLogger(LoadBalancer.class);

    public static String nextNodeUrl(String key, Collection<String> urls) {
        if (urls.isEmpty()) {
            throw new IllegalArgumentException("Got empty array of URL's for hashing");
        }

        int maxHash = Integer.MIN_VALUE;
        String maxHashUrl = null;
        for (String url : urls) {
            int hash = hash(key, url, urls.size());
            if (hash >= maxHash) {
                maxHash = hash;
                maxHashUrl = url;
            }
        }
        return maxHashUrl;
    }

    private static int hash(String key, String serviceUrl, int size) {
        return HashUtils.getMurmur128Hash(key + serviceUrl) % size;
    }

    public void balance(MyServiceBase service, Request request, HttpSession session)
            throws IOException, ExecutionException, InterruptedException {
        String id = request.getParameter("id=");
        String nextNodeUrl = nextNodeUrl(id, nodes.keySet());
        Node nextNode = nodes.get(nextNodeUrl);
        Response response;

        if (nextNode.isIll()) {
            response = MyServiceBase.emptyResponseForCode(Response.NOT_FOUND);
            session.sendResponse(response);
            return;
        }

        switch (request.getMethod()) {
            case Request.METHOD_GET
                    -> response = handleRequest(id, request, service::handleGet, service, nextNodeUrl);
            case Request.METHOD_PUT
                    -> response = handleRequest(id, request, service::handlePut, service, nextNodeUrl);
            case Request.METHOD_DELETE
                    -> response = handleRequest(id, request, service::handleDelete, service, nextNodeUrl);
            // never throws
            default -> throw new IllegalArgumentException("Unexpected request method to be balanced: "
                    + request.getMethod());
        }
        session.sendResponse(response);
    }

    public void addUrls(List<String> urls) {
        if (nodes.isEmpty()) {
            urls.forEach(url -> nodes.put(url, new Node(url)));
        }
    }

    public void remove(String url) {
        nodes.remove(url);
    }

    private Response handleRequest(String id, Request request, Handler handler, MyServiceBase service, String nodeUrl) {
        Response response = null;
        Node node = nodes.get(nodeUrl);
        if (service.selfUrl().equals(nodeUrl)) {
            response = nodeRequest(id, request, node, handler);
        } else if (nodes.size() != 1) {
            response = nodeRequest(id, request, node, node::proxyRequest);
        }
        return response;
    }

    private Response nodeRequest(String id, Request request, Node node, Handler handler) {
        try {
            return handler.handle(id, request);
        } catch (Exception ex) {
            node.setIll(true);
            log.debug("Node {} is now ill", node.selfUrl());
            return MyServiceBase.emptyResponseForCode(Response.NOT_FOUND);
        }
    }

    private interface Handler {
        Response handle(String id, Request request) throws Exception;
    }

}
