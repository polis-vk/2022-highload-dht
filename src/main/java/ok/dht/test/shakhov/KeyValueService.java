package ok.dht.test.shakhov;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.shakhov.dao.BaseEntry;
import ok.dht.test.shakhov.dao.Dao;
import ok.dht.test.shakhov.dao.DaoConfig;
import ok.dht.test.shakhov.dao.Entry;
import ok.dht.test.shakhov.dao.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.util.Hash;
import one.nio.util.Utf8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import static ok.dht.test.shakhov.HttpUtils.X_LEADER_TIMESTAMP_HEADER;
import static ok.dht.test.shakhov.HttpUtils.convertToOneNioResponse;
import static ok.dht.test.shakhov.HttpUtils.createHttpServerConfigFromPort;
import static ok.dht.test.shakhov.HttpUtils.internalError;
import static ok.dht.test.shakhov.HttpUtils.methodNotAllowed;
import static ok.dht.test.shakhov.ResponseAggregator.aggregateDeleteResponses;
import static ok.dht.test.shakhov.ResponseAggregator.aggregateGetResponses;
import static ok.dht.test.shakhov.ResponseAggregator.aggregatePutResponses;

public class KeyValueService implements Service {
    private static final Logger log = LoggerFactory.getLogger(KeyValueService.class);

    private static final int FLUSH_THRESHOLD_BYTES = 4 * 1024 * 1024; // 4 mb
    private static final Duration INTERNAL_COMMUNICATION_TIMEOUT = Duration.ofSeconds(10);

    private final ServiceConfig serviceConfig;
    private final Clock clock;

    private HttpServer server;
    private HttpClient httpClient;
    private Dao<MemorySegment, Entry<MemorySegment>> dao;

    public KeyValueService(ServiceConfig serviceConfig) {
        this.serviceConfig = serviceConfig;
        this.clock = Clock.systemUTC();
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        DaoConfig daoConfig = new DaoConfig(serviceConfig.workingDir(), FLUSH_THRESHOLD_BYTES);
        dao = new MemorySegmentDao(daoConfig);
        httpClient = HttpClient.newBuilder()
                .connectTimeout(INTERNAL_COMMUNICATION_TIMEOUT)
                .build();
        HttpServerConfig httpServerConfig = createHttpServerConfigFromPort(serviceConfig.selfPort());
        server = new KeyValueHttpServer(
                httpServerConfig,
                serviceConfig.clusterUrls().size(),
                this::handleClientRequest,
                this::handleInternalRequest
        );
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        dao.close();
        return CompletableFuture.completedFuture(null);
    }

    private Response handleClientRequest(Request request, String id, int ack, int from) {
        try {
            long timestamp = clock.millis();
            Queue<Node> nodes = getNodesForKey(id, from);
            List<Response> responses = new ArrayList<>(from);
            for (Node node : nodes) {
                collectResponseFromNode(node, request, id, timestamp, responses);
            }

            return switch (request.getMethod()) {
                case Request.METHOD_GET -> aggregateGetResponses(responses, ack);
                case Request.METHOD_PUT -> aggregatePutResponses(responses, ack);
                case Request.METHOD_DELETE -> aggregateDeleteResponses(responses, ack);
                default -> methodNotAllowed();
            };
        } catch (Exception e) {
            log.error("Unexpected error during processing client {}", request, e);
            return internalError();
        }
    }

    private Queue<Node> getNodesForKey(String key, int limit) {
        List<String> clusterUrls = serviceConfig.clusterUrls();

        Queue<Node> limitedNodes = new PriorityQueue<>(limit + 1);
        for (int i = 0; i < clusterUrls.size(); i++) {
            Node node = new Node(clusterUrls.get(i), Hash.murmur3(key + i));
            limitedNodes.offer(node);

            if (limitedNodes.size() > limit) {
                limitedNodes.poll();
            }
        }

        return limitedNodes;
    }

    private void collectResponseFromNode(Node node,
                                         Request request,
                                         String id,
                                         long timestamp,
                                         List<Response> responses) {
        if (!serviceConfig.selfUrl().equals(node.url)) {
            try {
                Response response = sendInternalRequest(request, node.url, timestamp);
                responses.add(response);
            } catch (Exception e) {
                log.warn("Can't access node {}", node.url, e);
            }
        } else {
            Response selfResponse = handleInternalRequest(request, id, timestamp);
            responses.add(selfResponse);
        }
    }

    private Response sendInternalRequest(Request clientRequest,
                                         String url,
                                         long timestamp) throws IOException, InterruptedException {
        byte[] requestBody = clientRequest.getBody();
        HttpRequest.BodyPublisher bodyPublisher;
        if (requestBody != null) {
            bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(requestBody);
        } else {
            bodyPublisher = HttpRequest.BodyPublishers.noBody();
        }

        HttpRequest internalRequest = HttpRequest.newBuilder(URI.create(url + clientRequest.getURI()))
                .method(clientRequest.getMethodName(), bodyPublisher)
                .header(X_LEADER_TIMESTAMP_HEADER, String.valueOf(timestamp))
                .timeout(INTERNAL_COMMUNICATION_TIMEOUT)
                .build();
        HttpResponse<byte[]> response = httpClient.send(internalRequest, HttpResponse.BodyHandlers.ofByteArray());
        return convertToOneNioResponse(response);
    }

    private Response handleInternalRequest(Request request, String id, long timestamp) {
        try {
            return switch (request.getMethod()) {
                case Request.METHOD_GET -> handleGet(id);
                case Request.METHOD_PUT -> handlePut(id, request.getBody(), timestamp);
                case Request.METHOD_DELETE -> handleDelete(id, timestamp);
                default -> methodNotAllowed();
            };
        } catch (Exception e) {
            log.error("Unexpected error during processing internal {}", request, e);
            return internalError();
        }
    }

    private Response handleGet(String id) throws IOException {
        MemorySegment key = MemorySegment.ofArray(Utf8.toBytes(id));
        Entry<MemorySegment> entry = dao.get(key);
        Response response;
        if (entry == null || entry.isTombstone()) {
            response = new Response(Response.NOT_FOUND, Response.EMPTY);
        } else {
            response = new Response(Response.OK, entry.value().toByteArray());
        }
        long timestamp = entry != null ? entry.timestamp() : Long.MIN_VALUE;
        response.addHeader(HttpUtils.ONE_NIO_X_RECORD_TIMESTAMP_HEADER + timestamp);
        return response;
    }

    private Response handlePut(String id, byte[] body, long timestamp) {
        MemorySegment key = MemorySegment.ofArray(Utf8.toBytes(id));
        MemorySegment value = MemorySegment.ofArray(body);
        Entry<MemorySegment> entry = new BaseEntry<>(key, timestamp, value);
        dao.upsert(entry);
        return new Response(Response.CREATED, Response.EMPTY);
    }

    private Response handleDelete(String id, long timestamp) {
        MemorySegment key = MemorySegment.ofArray(Utf8.toBytes(id));
        Entry<MemorySegment> entry = new BaseEntry<>(key, timestamp, null);
        dao.upsert(entry);
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    private static class Node implements Comparable<Node> {
        final String url;
        final long hash;

        Node(String url, long hash) {
            this.url = url;
            this.hash = hash;
        }

        @Override
        public int compareTo(Node o) {
            return Long.compare(hash, o.hash);
        }
    }

    @ServiceFactory(stage = 4, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class StorageServiceFactory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new KeyValueService(config);
        }
    }
}
