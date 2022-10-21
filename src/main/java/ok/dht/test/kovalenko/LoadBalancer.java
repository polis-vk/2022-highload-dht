package ok.dht.test.kovalenko;

import ok.dht.test.kovalenko.utils.HashUtils;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class LoadBalancer {

    private static final Map<String, Node> nodes = new HashMap<>();

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

        try {
            switch (request.getMethod()) {
                case Request.METHOD_GET -> {
                    if (service.selfUrl().equals(nextNodeUrl)) {
                        response = service.handleGet(id);
                    } else {
                        response = nextNode.proxyRequest(id, request);
                    }
                }
                case Request.METHOD_PUT -> {
                    if (service.selfUrl().equals(nextNodeUrl)) {
                        response = service.handlePut(id, request);
                    } else {
                        response = nextNode.proxyRequest(id, request);
                    }
                }
                case Request.METHOD_DELETE -> {
                    if (service.selfUrl().equals(nextNodeUrl)) {
                        response = service.handleDelete(id);
                    } else {
                        response = nextNode.proxyRequest(id, request);
                    }
                }
                default -> throw new IllegalArgumentException("Unexpected request method to be balanced: "
                        + request.getMethod());
            }
        } catch (Exception ex) {
            response = MyServiceBase.emptyResponseForCode(Response.SERVICE_UNAVAILABLE);
        }
        if (response.getHeaders()[0].equals(Response.SERVICE_UNAVAILABLE)) {
            nextNode.setIll(true);
            response = MyServiceBase.emptyResponseForCode(Response.NOT_FOUND);
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
}
