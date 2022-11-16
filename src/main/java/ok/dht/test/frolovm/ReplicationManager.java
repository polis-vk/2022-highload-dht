package ok.dht.test.frolovm;

import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.util.ByteArrayBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ReplicationManager {

    public static final String NOT_ENOUGH_REPLICAS = "Not Enough Replicas";
    public static final int CHUNK_REQUEST_CAPACITY = 512;
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(4);
    private static final Logger LOGGER = LoggerFactory.getLogger(ReplicationManager.class);
    public static final char DELIMETER = '\n';
    private final ShardingAlgorithm algorithm;
    private final String selfUrl;
    private final RequestExecutor requestExecutor;
    private final CircuitBreaker circuitBreaker;
    private final HttpClient client;

    public ReplicationManager(
            ShardingAlgorithm algorithm,
            String selfUrl,
            RequestExecutor requestExecutor,
            CircuitBreaker circuitBreaker,
            HttpClient client) {
        this.algorithm = algorithm;
        this.selfUrl = selfUrl;
        this.requestExecutor = requestExecutor;
        this.circuitBreaker = circuitBreaker;
        this.client = client;
    }

    private static void addHeader(Response resultResponse, HttpHeaders headers, String name, String nioName) {
        headers.firstValue(name).ifPresent(s -> resultResponse.addHeader(nioName + s));
    }

    private static HttpRequest.Builder getBuilder(Request request, String uri, byte[] body) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
        requestBuilder.header(Utils.TIMESTAMP, request.getHeader(Utils.TIMESTAMP_ONE_NIO));
        requestBuilder.uri(URI.create(uri));
        requestBuilder.method(
                request.getMethodName(),
                HttpRequest.BodyPublishers.ofByteArray(body));
        requestBuilder.timeout(RESPONSE_TIMEOUT);
        return requestBuilder;
    }

    private static Response createInternalResponse(HttpResponse<byte[]> response) {
        Response resultResponse = new Response(Utils.getResponseStatus(response), response.body());
        HttpHeaders headers = response.headers();
        addHeader(resultResponse, headers, Utils.TOMBSTONE, Utils.TOMBSTONE);
        addHeader(resultResponse, headers, Utils.TIMESTAMP, Utils.TIMESTAMP_ONE_NIO);

        return resultResponse;
    }

    public void handle(String id, Request request, HttpSession session, int ack, int from) {
        if (Utils.isInternal(request)) {
            Utils.sendResponse(session, requestExecutor.entityHandlerSelf(id, request, Utils.getTimestamp(request)));
            return;
        }
        final int ackNumber = ack;
        final long timestamp = System.currentTimeMillis();
        request.addHeader(Utils.TIMESTAMP_ONE_NIO + timestamp);

        List<Response> collectedResponses = new CopyOnWriteArrayList<>();
        AtomicInteger countReq = new AtomicInteger(0);
        int shardIndex = algorithm.chooseShard(id);

        for (int i = 0; i < from; ++i) {
            Shard shard = algorithm.getShardByIndex(shardIndex);

            handleFinder(id, request, timestamp, shard).thenComposeAsync(
                    response -> {
                        addSuccessResponse(collectedResponses, response);
                        countReq.incrementAndGet();
                        return null;
                    }
            ).whenCompleteAsync(
                    (resp, exception) -> {
                        final int currentCount = countReq.get();
                        final int currentSize = collectedResponses.size();
                        boolean cantBeSuccess = (from - currentCount) < ackNumber - currentSize;
                        if (cantBeSuccess) {
                            if (canISendResponse(from, countReq, currentCount)) {
                                Utils.sendResponse(
                                        session,
                                        new Response(Response.GATEWAY_TIMEOUT, Utils.stringToByte(NOT_ENOUGH_REPLICAS))
                                );
                            }
                        } else if (currentSize >= ackNumber && canISendResponse(from, countReq, currentCount)) {
                            Utils.sendResponse(session, generateResult(collectedResponses, request.getMethod()));
                        }
                    }
            );

            shardIndex = (shardIndex + 1) % algorithm.getShards().size();
        }
    }

    private static boolean canISendResponse(int from, AtomicInteger countReq, int currentCount) {
        return currentCount < from + 1 && countReq.compareAndSet(currentCount, from + 1);
    }

    private CompletableFuture<Response> handleFinder(String id, Request request, long timestamp, Shard shard) {
        if (shard.getName().equals(selfUrl)) {
            return CompletableFuture.completedFuture(requestExecutor.entityHandlerSelf(id, request, timestamp));
        } else {
            if (circuitBreaker.isReady(shard.getName())) {
                return getResponseFromAnotherNode(request, shard);
            } else {
                LOGGER.error("Node is unavailable right now");
                return CompletableFuture.completedFuture(Utils.emptyResponse(Response.SERVICE_UNAVAILABLE));
            }
        }
    }

    private void addSuccessResponse(List<Response> collectedResponses, Response response) {
        if (isSuccessful(response)) {
            collectedResponses.add(response);
        }
    }

    private Response generateResult(List<Response> collectedResponses, int methodType) {

        switch (methodType) {
            case Request.METHOD_GET: {
                return getRequestProcessing(collectedResponses);
            }
            case Request.METHOD_PUT: {
                return Utils.emptyResponse(Response.CREATED);
            }
            case Request.METHOD_DELETE: {
                return Utils.emptyResponse(Response.ACCEPTED);
            }
            default:
                return Utils.emptyResponse(Response.METHOD_NOT_ALLOWED);
        }
    }

    private Response getRequestProcessing(List<Response> collectedResponses) {
        byte[] result = null;
        long mostRelevantTimestamp = 0L;
        boolean isResultTombstone = false;
        for (Response response : collectedResponses) {
            long timestamp = Utils.getTimestamp(response);
            boolean isTombstone = response.getHeader(Utils.TOMBSTONE) != null;
            if (mostRelevantTimestamp < timestamp
                    && (response.getStatus() == HttpURLConnection.HTTP_OK || isTombstone)) {
                isResultTombstone = isTombstone;
                mostRelevantTimestamp = timestamp;
                result = response.getBody();
            }
        }
        if (result == null || isResultTombstone) {
            return Utils.emptyResponse(Response.NOT_FOUND);
        } else {
            return new Response(Response.OK, result);
        }
    }

    private boolean isSuccessful(Response response) {
        return !Utils.isServerError(response.getStatus())
                && (!Utils.is4xxError(response.getStatus())
                || response.getStatus() == HttpURLConnection.HTTP_NOT_FOUND);
    }

    private CompletableFuture<Response> getResponseFromAnotherNode(Request request, Shard shard) {
        byte[] body = request.getBody() == null ? Response.EMPTY : request.getBody();
        HttpRequest.Builder requestBuilder = getBuilder(request, shard.getName() + request.getURI(), body);
        return client.sendAsync(requestBuilder.build(),
                HttpResponse.BodyHandlers.ofByteArray()).handleAsync((response, exception) -> {
                    if (exception != null || Utils.isServerError(response.statusCode())) {
                        circuitBreaker.incrementFail(shard.getName());
                        return Utils.emptyResponse(Response.GATEWAY_TIMEOUT);
                    } else {
                        circuitBreaker.successRequest(shard.getName());
                        return createInternalResponse(response);
                    }
                }
        );
    }

    public void handleRange(String start, String end, HttpSession session) {
        Iterator<Pair<byte[], byte[]>> iterator = requestExecutor.entityHandlerRange(start, end);
        Utils.sendResponse(session, RangeResponse.openChunks());
        ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        while (iterator.hasNext()) {
            Pair<byte[], byte[]> current = iterator.next();
            byteArrayBuilder.append(current.getKey()).append(DELIMETER).append(current.getValue());
            if (CHUNK_REQUEST_CAPACITY < byteArrayBuilder.length()) {
                Utils.sendResponse(session, RangeResponse.createOneChunk(byteArrayBuilder.toBytes()));
            }
        }
        if (byteArrayBuilder.length() != 0) {
            Utils.sendResponse(session, RangeResponse.createOneChunk(byteArrayBuilder.toBytes()));
        }
        Utils.sendResponse(session, RangeResponse.ENDING_RESPONSE);
    }
}
