package ok.dht.test.yasevich;

import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.util.Hash;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ShardingRouter {
    public static final int LOGICAL_NODES_PER_PHYSICAL = 4;
    public static final int ILL_NODE_SKIPPED_REQUESTS = 100;
    public static final int FAILED_REQUESTS_THRESHOLD = 5;
    public static final int TIME_OUT_MS = 30;

    private final List<Node> vNodes;
    private final HttpClient httpClient = HttpClient.newHttpClient();


    public ShardingRouter(List<String> clusterUrls) {
        List<Node> physicalNodes = new ArrayList<>(clusterUrls.size());
        for (String url : clusterUrls) {
            physicalNodes.add(new Node(url));
        }
        this.vNodes = assignVNodes(physicalNodes);
    }

    private List<Node> assignVNodes(List<Node> physicalNodes) {
        int logicalNodes = physicalNodes.size() * LOGICAL_NODES_PER_PHYSICAL;
        List<Node> vNodes = new ArrayList<>(logicalNodes);
        Random random = new Random(System.currentTimeMillis() / (1000 * 60 * 60 * 24));
        Map<Node, Integer> nodeAssignments = new HashMap<>();
        for (int i = 0; i < logicalNodes; i++) {
            Node node;
            do {
                node = physicalNodes.get(random.nextInt(0, physicalNodes.size()));
            } while (nodeAssignments.computeIfAbsent(node, s -> 0) >= LOGICAL_NODES_PER_PHYSICAL);
            nodeAssignments.computeIfPresent(node, (s, integer) -> integer + 1);
            vNodes.add(node);
        }
        return vNodes;
    }

    public Response routeRequest(String id, Request request) {
        Node vNode = vNode(id);
        return vNode.routeRequest(httpClient, id, request);
    }

    public boolean isSelfResponsible(String id, String url) {
        Node vNode = vNode(id);
        return vNode.url.equals(url);
    }

    private Node vNode(String id) {
        int hash = Math.abs(Hash.murmur3(id));
        return vNodes.get(hash % vNodes.size());
    }

    private static HttpRequest httpRequestOf(String key, String targetUrl, Request request) {
        HttpRequest.Builder requestBuilder = HttpRequest
                .newBuilder(URI.create(targetUrl + "/v0/entity?id=" + key));
        switch (request.getMethod()) {
            case Request.METHOD_GET -> requestBuilder.GET();
            case Request.METHOD_PUT -> requestBuilder.PUT(HttpRequest.BodyPublishers.ofByteArray(request.getBody()));
            case Request.METHOD_DELETE -> requestBuilder.DELETE();
            default -> throw new IllegalArgumentException();
        }
        return requestBuilder.build();
    }

    private static class Node {
        private final AtomicInteger counter = new AtomicInteger();
        private final String url;
        private boolean isIll;

        private Node(String url) {
            this.url = url;
        }

        public Response routeRequest(HttpClient httpClient, String key, Request request) {

            if (isIll) {
                managePossibleRecovery();
                if (isIll) {
                    return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
                }
            }

            HttpRequest httpRequest = httpRequestOf(key, url, request);
            HttpResponse<byte[]> httpResponse;

            try {
                CompletableFuture<HttpResponse<byte[]>> httpResponseFuture =
                        httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
                httpResponse = httpResponseFuture.get(TIME_OUT_MS, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                managePossibleIllness();
                return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
            }

            return new Response(String.valueOf(httpResponse.statusCode()), httpResponse.body());
        }

        private void managePossibleRecovery() {
            if (counter.incrementAndGet() >= ILL_NODE_SKIPPED_REQUESTS) {
                isIll = false;
                counter.set(0);
            }
        }

        private void managePossibleIllness() {
            if (counter.incrementAndGet() >= FAILED_REQUESTS_THRESHOLD) {
                isIll = true;
                counter.set(0);
            }
        }

        @Override
        public int hashCode() {
            return url.hashCode();
        }

    }

}
