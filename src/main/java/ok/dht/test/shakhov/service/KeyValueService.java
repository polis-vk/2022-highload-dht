package ok.dht.test.shakhov.service;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.shakhov.concurrent.DefaultThreadPoolManager;
import ok.dht.test.shakhov.dao.BaseEntry;
import ok.dht.test.shakhov.dao.Dao;
import ok.dht.test.shakhov.dao.DaoConfig;
import ok.dht.test.shakhov.dao.Entry;
import ok.dht.test.shakhov.dao.MemorySegmentDao;
import ok.dht.test.shakhov.http.HttpUtils;
import ok.dht.test.shakhov.http.response.ResponseHandler;
import ok.dht.test.shakhov.http.response.ResponseWithTimestamp;
import ok.dht.test.shakhov.http.server.KeyValueHttpServer;
import ok.dht.test.shakhov.http.stream.StreamResponse;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.util.Hash;
import one.nio.util.Utf8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static java.net.HttpURLConnection.HTTP_ACCEPTED;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static ok.dht.test.shakhov.http.HttpClientFactory.createHttpClient;
import static ok.dht.test.shakhov.http.HttpUtils.NOT_ENOUGH_REPLICAS;
import static ok.dht.test.shakhov.http.HttpUtils.X_LEADER_TIMESTAMP_HEADER;
import static ok.dht.test.shakhov.http.HttpUtils.createHttpServerConfigFromPort;
import static ok.dht.test.shakhov.http.HttpUtils.internalError;
import static ok.dht.test.shakhov.http.HttpUtils.methodNotAllowed;

public class KeyValueService implements Service {
    private static final Logger log = LoggerFactory.getLogger(KeyValueService.class);

    private static final int FLUSH_THRESHOLD_BYTES = 1 * 1024 * 1024; // 1 mb
    private static final Duration INTERNAL_COMMUNICATION_TIMEOUT = Duration.ofSeconds(10);
    private static final Set<Integer> ACK_STATUSES = Set.of(HTTP_OK, HTTP_CREATED, HTTP_ACCEPTED, HTTP_NOT_FOUND);

    private final ServiceConfig serviceConfig;
    private final Clock clock;

    private HttpServer server;
    private Map<String, HttpClient> urlToHttpClient;
    private ThreadPoolExecutor internalExecutor;
    private Dao<MemorySegment, Entry<MemorySegment>> dao;

    public KeyValueService(ServiceConfig serviceConfig, Clock clock) {
        this.serviceConfig = serviceConfig;
        this.clock = clock;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        DaoConfig daoConfig = new DaoConfig(serviceConfig.workingDir(), FLUSH_THRESHOLD_BYTES);
        dao = new MemorySegmentDao(daoConfig);

        internalExecutor = DefaultThreadPoolManager.createThreadPool("internal");

        urlToHttpClient = new HashMap<>();
        for (String url : serviceConfig.clusterUrls()) {
            if (!serviceConfig.selfUrl().equals(url)) {
                urlToHttpClient.put(url, createHttpClient(url));
            }
        }

        HttpServerConfig httpServerConfig = createHttpServerConfigFromPort(serviceConfig.selfPort());
        server = new KeyValueHttpServer(
                httpServerConfig,
                serviceConfig.clusterUrls().size(),
                this::handleClientRequestAsync,
                this::handleInternalRequestAsync,
                this::handleStreamRequestAsync
        );
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        DefaultThreadPoolManager.shutdownThreadPool(internalExecutor);
        dao.close();
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Response> handleClientRequestAsync(Request request, String id, int ack, int from) {
        try {
            long timestamp = clock.millis();
            List<CompletableFuture<ResponseWithTimestamp>> responses = new ArrayList<>(from);
            List<String> urls = getUrlsForKey(id, from);
            for (String url : urls) {
                if (!serviceConfig.selfUrl().equals(url)) {
                    responses.add(sendInternalRequestAsync(request, url, timestamp));
                } else {
                    CompletableFuture<ResponseWithTimestamp> selfResponse =
                            handleInternalRequestAsync(request, id, timestamp)
                                    .thenApply(ResponseWithTimestamp::new);
                    responses.add(selfResponse);
                }
            }
            return ResponseHandler.getAckResponses(responses, ACK_STATUSES, ack, from)
                    .handle(KeyValueService::aggregateAckResponses);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private List<String> getUrlsForKey(String key, int size) {
        List<String> clusterUrls = serviceConfig.clusterUrls();

        int argmax = -1;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < clusterUrls.size(); i++) {
            int candidate = Hash.murmur3(key + i);
            if (candidate >= max) {
                max = candidate;
                argmax = i;
            }
        }

        List<String> urls = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String url = clusterUrls.get((argmax + i) % clusterUrls.size());
            urls.add(url);
        }

        return urls;
    }

    private CompletableFuture<ResponseWithTimestamp> sendInternalRequestAsync(Request clientRequest,
                                                                              String url,
                                                                              long timestamp) {
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

        return urlToHttpClient.get(url)
                .sendAsync(internalRequest, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(ResponseWithTimestamp::new);
    }

    private CompletableFuture<Response> handleInternalRequestAsync(Request request, String id, long timestamp) {
        return CompletableFuture.supplyAsync(() -> handleInternalRequest(request, id, timestamp), internalExecutor);
    }

    private Response handleInternalRequest(Request request, String id, long timestamp) {
        try {
            return switch (request.getMethod()) {
                case Request.METHOD_GET -> handleGet(id);
                case Request.METHOD_PUT -> handlePut(id, request.getBody(), timestamp);
                case Request.METHOD_DELETE -> handleDelete(id, timestamp);
                default -> methodNotAllowed();
            };
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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

    private static Response aggregateAckResponses(AtomicReferenceArray<ResponseWithTimestamp> responses, Throwable t) {
        if (t != null) {
            log.warn("Couldn't collect ack responses", t);
            return new Response(NOT_ENOUGH_REPLICAS, Response.EMPTY);
        }

        ResponseWithTimestamp winResponse = null;
        long maxTimestamp = Long.MIN_VALUE;
        for (int i = 0; i < responses.length(); i++) {
            ResponseWithTimestamp response = responses.get(i);
            if (response != null && response.timestamp() >= maxTimestamp) {
                maxTimestamp = response.timestamp();
                winResponse = response;
            }
        }

        if (winResponse == null) {
            throw new IllegalStateException("There are no responses");
        }

        return new Response(String.valueOf(winResponse.statusCode()), winResponse.body());
    }

    private Response handleStreamRequestAsync(Request request, String start, String end) {
        try {
            MemorySegment startSegment = MemorySegment.ofArray(Utf8.toBytes(start));
            MemorySegment endSegment = end == null ? null : MemorySegment.ofArray(Utf8.toBytes(end));
            Iterator<Entry<MemorySegment>> streamIterator = dao.get(startSegment, endSegment);
            return new StreamResponse(streamIterator);
        } catch (Exception e) {
            log.error("Unexpected error during processing stream {}", request, e);
            return internalError();
        }
    }
}
