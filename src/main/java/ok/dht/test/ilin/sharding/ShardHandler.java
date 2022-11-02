package ok.dht.test.ilin.sharding;

import ok.dht.test.ilin.domain.Headers;
import ok.dht.test.ilin.hashing.impl.ConsistentHashing;
import one.nio.http.Request;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class ShardHandler {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final ConsistentHashing consistentHashing;
    private final String selfAddress;
    private final Logger logger = LoggerFactory.getLogger(ShardHandler.class);
    private final HttpClient httpClient;

    public ShardHandler(
        String selfAddress,
        ConsistentHashing consistentHashing
    ) {
        this.consistentHashing = consistentHashing;
        this.selfAddress = selfAddress;
        this.httpClient = HttpClient.newBuilder().build();
    }

    public CompletableFuture<Response> executeRequest(String key, Request request) {
        final String address = consistentHashing.getServerAddressFromKey(key);
        return executeOnAddress(address, key, request);
    }

    public CompletableFuture<Response> executeOnAddress(String address, String key, Request request) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(address.concat("/v0/entity?id=").concat(key)))
            .timeout(DEFAULT_TIMEOUT)
            .header(Headers.JAVA_NET_TIMESTAMP_HEADER, request.getHeader(Headers.TIMESTAMP_HEADER));
        try {
            return switch (request.getMethod()) {
                case Request.METHOD_GET -> {
                    CompletableFuture<HttpResponse<byte[]>> responseFuture = httpClient.sendAsync(
                        requestBuilder.GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray()
                    );
                    yield responseFuture.thenApply(response -> {
                        Response result = new Response(String.valueOf(response.statusCode()), response.body());
                        response.headers()
                            .firstValue(Headers.JAVA_NET_TIMESTAMP_HEADER)
                            .ifPresent(x -> result.addHeader(Headers.TIMESTAMP_HEADER + x));
                        response.headers()
                            .firstValue(Headers.JAVA_NET_TOMBSTONE_HEADER)
                            .ifPresent(x -> result.addHeader(Headers.TOMBSTONE_HEADER));
                        return result;
                    });
                }
                case Request.METHOD_PUT -> {
                    CompletableFuture<HttpResponse<byte[]>> responseFuture = httpClient.sendAsync(
                        requestBuilder.PUT(HttpRequest.BodyPublishers.ofByteArray(request.getBody())).build(),
                        HttpResponse.BodyHandlers.ofByteArray()
                    );
                    yield responseFuture.thenApply(response -> new Response(
                        String.valueOf(response.statusCode()),
                        response.body()
                    ));
                }
                case Request.METHOD_DELETE -> {
                    CompletableFuture<HttpResponse<byte[]>> responseFuture = httpClient.sendAsync(
                        requestBuilder.DELETE().build(),
                        HttpResponse.BodyHandlers.ofByteArray()
                    );
                    yield responseFuture.thenApply(response -> new Response(
                        String.valueOf(response.statusCode()),
                        response.body()
                    ));
                }
                default -> CompletableFuture.supplyAsync(() -> new Response(
                    Response.METHOD_NOT_ALLOWED,
                    Response.EMPTY
                ), Runnable::run);
            };
        } catch (Exception e) {
            logger.error("failed execute request: {}", e.getMessage());
            return CompletableFuture.supplyAsync(() -> new Response(Response.INTERNAL_ERROR, Response.EMPTY));
        }
    }

    public boolean isForSelf(String key) {
        return selfAddress.equals(consistentHashing.getServerAddressFromKey(key));
    }
}
