package ok.dht.test.ilin.sharding;

import ok.dht.test.ilin.domain.HeadersUtils;
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

    public Response executeRequest(String key, Request request) {
        final String address = consistentHashing.getServerAddressFromKey(key);
        return executeOnAddress(address, key, request);
    }

    public Response executeOnAddress(String address, String key, Request request) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(address.concat("/v0/entity?id=").concat(key)))
            .timeout(DEFAULT_TIMEOUT)
            .header(HeadersUtils.JAVA_NET_TIMESTAMP_HEADER, request.getHeader(HeadersUtils.TIMESTAMP_HEADER));
        try {
            return switch (request.getMethod()) {
                case Request.METHOD_GET -> {
                    HttpResponse<byte[]> response = httpClient.send(
                        requestBuilder.GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray()
                    );
                    Response result = new Response(String.valueOf(response.statusCode()), response.body());
                    response.headers()
                        .firstValue(HeadersUtils.JAVA_NET_TIMESTAMP_HEADER)
                        .ifPresent(x -> result.addHeader(HeadersUtils.TIMESTAMP_HEADER + x));
                    response.headers()
                        .firstValue(HeadersUtils.JAVA_NET_TOMBSTONE_HEADER)
                        .ifPresent(x -> result.addHeader(HeadersUtils.TOMBSTONE_HEADER));
                    yield result;
                }
                case Request.METHOD_PUT -> {
                    HttpResponse<byte[]> response = httpClient.send(
                        requestBuilder.PUT(HttpRequest.BodyPublishers.ofByteArray(request.getBody())).build(),
                        HttpResponse.BodyHandlers.ofByteArray()
                    );
                    yield new Response(String.valueOf(response.statusCode()), response.body());
                }
                case Request.METHOD_DELETE -> {
                    HttpResponse<byte[]> response = httpClient.send(
                        requestBuilder.DELETE().build(),
                        HttpResponse.BodyHandlers.ofByteArray()
                    );
                    yield new Response(String.valueOf(response.statusCode()), response.body());
                }
                default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            };
        } catch (HttpTimeoutException e) {
            logger.error("execute request timeout: {}", e.getMessage());
            return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
        } catch (Exception e) {
            logger.error("failed execute request: {}", e.getMessage());
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    public boolean isForSelf(String key) {
        return selfAddress.equals(consistentHashing.getServerAddressFromKey(key));
    }
}
