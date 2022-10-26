package ok.dht.test.frolovm;

import one.nio.http.Request;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class ReplicationManager {

    public static final String NOT_ENOUGH_REPLICAS = "Not Enough Replicas";
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(1);
    private static final Logger LOGGER = LoggerFactory.getLogger(ReplicationManager.class);
    private final ShardingAlgorithm algorithm;
    private final String selfUrl;
    private final RequestExecutor requestExecutor;
    private final CircuitBreaker circuitBreaker;
    private final HttpClient client;

    public ReplicationManager(ShardingAlgorithm algorithm, String selfUrl, RequestExecutor requestExecutor, CircuitBreaker circuitBreaker,
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

    private static Response createInternalResponse(HttpResponse<byte[]> response, String responseStatus) {
        Response resultResponse = new Response(responseStatus, response.body());
        HttpHeaders headers = response.headers();
        addHeader(resultResponse, headers, Utils.TOMBSTONE, Utils.TOMBSTONE);
        addHeader(resultResponse, headers, Utils.TIMESTAMP, Utils.TIMESTAMP_ONE_NIO);

        return resultResponse;
    }

    public Response handle(String id, Request request, int ack, int from) {
        if (Utils.isInternal(request)) {
            return requestExecutor.entityHandlerSelf(id, request, Utils.getTimestamp(request));
        }
        final long timestamp = System.currentTimeMillis();
        request.addHeader(Utils.TIMESTAMP_ONE_NIO + timestamp);

        List<Response> collectedResponses = new ArrayList<>();
        int countAck = 0;
        int shardIndex = algorithm.chooseShard(id);

        for (int i = 0; i < from; ++i) {
            Shard shard = algorithm.getShardByIndex(shardIndex);

            if (shard.getName().equals(selfUrl)) {
                Response response = requestExecutor.entityHandlerSelf(id, request, timestamp);
                countAck = addSuccessResponse(request, collectedResponses, countAck, response);
            } else {
                if (circuitBreaker.isReady(shard.getName())) {
                    try {
                        Response response = sendResponseToAnotherNode(request, shard);
                        countAck = addSuccessResponse(request, collectedResponses, countAck, response);
                    } catch (IOException | InterruptedException e) {
                        LOGGER.error("Something bad happens when client answer", e);
                        circuitBreaker.incrementFail(shard.getName());
                    }
                } else {
                    LOGGER.error("Node is unavailable right now");
                }
            }
            if (countAck >= ack && request.getMethod() == Request.METHOD_GET) {
                return generateResult(collectedResponses, request.getMethod());
            }
            shardIndex = (shardIndex + 1) % algorithm.getShards().size();
        }
        if (countAck >= ack) {
            return generateResult(collectedResponses, request.getMethod());
        }
        return new Response(Response.GATEWAY_TIMEOUT, Utils.stringToByte(NOT_ENOUGH_REPLICAS));
    }

    private int addSuccessResponse(Request request, List<Response> collectedResponses,
                                   int countAck, Response response) {
        if (isSuccessful(response)) {
            if (request.getMethod() == Request.METHOD_GET) {
                collectedResponses.add(response);
            }
            return countAck + 1;
        } else {
            return countAck;
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

    private Response sendResponseToAnotherNode(Request request, Shard shard) throws IOException, InterruptedException {
        byte[] body = request.getBody() == null ? Response.EMPTY : request.getBody();
        HttpResponse<byte[]> response;

        try {
            HttpRequest.Builder requestBuilder = getBuilder(request, shard.getName() + request.getURI(), body);
            response = client.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpTimeoutException | SocketException exception) {
            circuitBreaker.incrementFail(shard.getName());
            LOGGER.debug("Can't connect to shard " + shard.getName());
            return Utils.emptyResponse(Response.GATEWAY_TIMEOUT);
        }

        if (Utils.isServerError(response.statusCode())) {
            circuitBreaker.incrementFail(shard.getName());
        } else {
            circuitBreaker.successRequest(shard.getName());
        }

        String responseStatus = Utils.getResponseStatus(response);

        return createInternalResponse(response, responseStatus);
    }

}
