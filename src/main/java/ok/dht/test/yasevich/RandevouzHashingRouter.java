package ok.dht.test.yasevich;

import one.nio.http.Request;
import one.nio.util.Hash;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToIntFunction;

public class RandevouzHashingRouter {
    public static final int ILL_NODE_SKIPPED_REQUESTS = 250;
    public static final int FAILED_REQUESTS_THRESHOLD = 50;

    private final List<Node> nodes;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public RandevouzHashingRouter(List<String> clusterUrls) {
        this.nodes = new ArrayList<>(clusterUrls.size());
        for (String url : clusterUrls) {
            nodes.add(new Node(url));
        }
    }

    public Queue<Node> responsibleNodes(String key, int nodesNumber) {
        Comparator<Node> comparator = Comparator
                .comparingInt((ToIntFunction<Node>) o -> Hash.murmur3(o.url + key))
                .reversed();

        Queue<Node> priorityQueue = new PriorityQueue<>(nodesNumber, comparator);

        for (Node node : nodes) {
            if (priorityQueue.size() < nodesNumber) {
                priorityQueue.add(node);
                continue;
            }
            Node lessPrioritized = priorityQueue.peek();
            if (comparator.compare(node, lessPrioritized) > 0) {
                priorityQueue.remove();
                priorityQueue.add(node);
            }
        }

        return priorityQueue;
    }

    private static HttpRequest innerHttpRequestOf(String key, String targetUrl, Request request) {
        HttpRequest.Builder requestBuilder = HttpRequest
                .newBuilder(URI.create(targetUrl + "/v0/entity?id=" + key + "&inner=true"));
        switch (request.getMethod()) {
            case Request.METHOD_GET -> requestBuilder.GET();
            case Request.METHOD_PUT -> requestBuilder.PUT(HttpRequest.BodyPublishers.ofByteArray(request.getBody()));
            case Request.METHOD_DELETE -> requestBuilder.DELETE();
            default -> throw new IllegalArgumentException();
        }
        return requestBuilder.build();
    }

    class Node {
        private final AtomicInteger counter = new AtomicInteger();
        final String url;
        private boolean isIll;

        public Node(String url) {
            this.url = url;
        }

        public CompletableFuture<HttpResponse<byte[]>> routedRequestFuture(Request request, String key) {
            if (isIll) {
                managePossibleRecovery();
                if (isIll) {
                    return CompletableFuture.failedFuture(new ConnectException("Node is ill"));
                }
            }
            HttpRequest httpRequest = innerHttpRequestOf(key, url, request);
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
