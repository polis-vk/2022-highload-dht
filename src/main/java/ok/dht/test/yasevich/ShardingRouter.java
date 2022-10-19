package ok.dht.test.yasevich;

import one.nio.http.Request;
import one.nio.util.Hash;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class ShardingRouter {
    public static final int VIRTUAL_NODES_PER_PHYSICAL = 4;
    public static final int ILL_NODE_SKIPPED_REQUESTS = 100;
    public static final int FAILED_REQUESTS_THRESHOLD = 5;

    private final List<Node> virtualNodes;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public ShardingRouter(List<String> clusterUrls) {
        List<Node> physicalNodes = new ArrayList<>(clusterUrls.size());
        for (String url : clusterUrls) {
            physicalNodes.add(new Node(url));
        }
        this.virtualNodes = assignVNodes(physicalNodes);
    }

    private List<Node> assignVNodes(List<Node> physicalNodes) {
        int virtualNodesCount = physicalNodes.size() * VIRTUAL_NODES_PER_PHYSICAL;
        List<Node> virtualNodes = new ArrayList<>(virtualNodesCount);
        Random random = new Random(System.currentTimeMillis() / (1000 * 60 * 60 * 24));
        Map<Node, Integer> nodeAssignments = new HashMap<>();
        for (int i = 0; i < virtualNodesCount; i++) {
            Node node;
            do {
                node = physicalNodes.get(random.nextInt(0, physicalNodes.size()));
            } while (nodeAssignments.computeIfAbsent(node, s -> 0) >= VIRTUAL_NODES_PER_PHYSICAL);
            nodeAssignments.computeIfPresent(node, (s, integer) -> integer + 1);
            virtualNodes.add(node);
        }
        return virtualNodes;
    }

    public CompletableFuture<HttpResponse<byte[]>> routedRequestFuture(Request request, String key) {
        Node vNode = virtualNodeByKey(key);
        return vNode.routedRequestFuture(httpClient, request, key);
    }

    public void informAboutFail(String key) {
        Node vNode = virtualNodeByKey(key);
        vNode.managePossibleIllness();
    }

    public boolean isUrlResponsibleForKey(String serverUrl, String key) {
        Node vNode = virtualNodeByKey(key);
        return vNode.url.equals(serverUrl);
    }

    private Node virtualNodeByKey(String id) {
        int hash = Math.abs(Hash.murmur3(id));
        return virtualNodes.get(hash % virtualNodes.size());
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

        public Node(String url) {
            this.url = url;
        }

        public CompletableFuture<HttpResponse<byte[]>> routedRequestFuture(
                HttpClient httpClient,
                Request request,
                String key
        ) {
            if (isIll) {
                managePossibleRecovery();
                if (isIll) {
                    return CompletableFuture.failedFuture(new NoSuchElementException());
                }
            }
            HttpRequest httpRequest = httpRequestOf(key, url, request);
            return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
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
