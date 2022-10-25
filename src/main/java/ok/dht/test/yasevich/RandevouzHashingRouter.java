package ok.dht.test.yasevich;

import one.nio.http.Request;
import one.nio.util.Hash;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class RandevouzHashingRouter {
    public static final int ILL_NODE_SKIPPED_REQUESTS = 100;
    public static final int FAILED_REQUESTS_THRESHOLD = 5;

    private final List<Node> nodes;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public RandevouzHashingRouter(List<String> clusterUrls, String selfUrl) {
        this.nodes = new ArrayList<>(clusterUrls.size());
        for (String url : clusterUrls) {
            nodes.add(new Node(url));
        }
    }


    public CompletableFuture<HttpResponse<byte[]>> routedRequestFuture(Request request, String key, Node responsibleNode) {
        return responsibleNode.routedRequestFuture(httpClient, request, key);
    }

    public Queue<Node> responsibleNodes(String key) {
        Queue<Node> queue = new PriorityQueue<>(Comparator.comparingInt(o -> Hash.murmur3(o.url + key)));
        queue.addAll(nodes);
        return queue;
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

    static class Node {
        private final AtomicInteger counter = new AtomicInteger();
        final String url;
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

        void managePossibleIllness() {
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
