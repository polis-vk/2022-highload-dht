package ok.dht.test.yasevich;

import one.nio.http.Request;
import one.nio.util.Hash;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.ToIntFunction;

public class RandevouzHashingRouter {
    public static final int FAILED_REQUESTS_THRESHOLD = 50;
    public static final int FAILED_REQUESTS_WINDOW_MS = 100;
    public static final int ILLNESS_PERIOD_MS = 100;

    private final List<Node> nodes;
    private final HttpClient httpClient;

    public RandevouzHashingRouter(List<String> clusterUrls, Executor executor) {
        this.httpClient = HttpClient.newBuilder().executor(executor).build();
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

    private static HttpRequest innerHttpRequestOf(String key, String targetUrl, Request request, long time) {
        HttpRequest.Builder requestBuilder = HttpRequest
                .newBuilder(URI.create(targetUrl + "/v0/entity?id=" + key));
        requestBuilder.header(ServiceImpl.COORDINATOR_TIMESTAMP_HEADER, String.valueOf(time));
        switch (request.getMethod()) {
            case Request.METHOD_GET -> requestBuilder.GET();
            case Request.METHOD_PUT -> requestBuilder.PUT(HttpRequest.BodyPublishers.ofByteArray(request.getBody()));
            case Request.METHOD_DELETE -> requestBuilder.DELETE();
            default -> throw new IllegalArgumentException();
        }
        return requestBuilder.build();
    }

    public class Node {
        final String url;
        private final Deque<Long> failedRequestsTimings = new ArrayDeque<>(FAILED_REQUESTS_THRESHOLD);
        private volatile long illnessStartTime = -1;

        private Node(String url) {
            this.url = url;
        }

        public CompletableFuture<HttpResponse<byte[]>> routedRequestFuture(Request request, String key, long time) {
            if (illnessStartTime > 0) {
                if (mayHaveRecovered()) {
                    illnessStartTime = -1;
                } else {
                    return CompletableFuture.failedFuture(new ConnectException("Node is ill"));
                }
            }
            HttpRequest httpRequest = innerHttpRequestOf(key, url, request, time);
            return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        }

        private boolean mayHaveRecovered() {
            long illnessStart = illnessStartTime;
            return illnessStart < 0 || System.currentTimeMillis() - illnessStart > ILLNESS_PERIOD_MS;
        }

        synchronized void managePossibleIllness() {
            long time = System.currentTimeMillis();
            if (failedRequestsTimings.size() < FAILED_REQUESTS_THRESHOLD) {
                failedRequestsTimings.add(time);
                return;
            }
            if (time - failedRequestsTimings.peek() < FAILED_REQUESTS_WINDOW_MS) {
                illnessStartTime = time;
                return;
            }
            failedRequestsTimings.pop();
            failedRequestsTimings.add(time);
        }

        @Override
        public int hashCode() {
            return url.hashCode();
        }
    }
}
