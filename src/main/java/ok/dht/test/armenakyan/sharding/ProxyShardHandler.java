package ok.dht.test.armenakyan.sharding;

import one.nio.http.Request;
import one.nio.http.Response;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

public class ProxyShardHandler implements ShardRequestHandler {
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String REQUEST_PATH = "/v0/entity?id=";

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private final String fullPath;

    public ProxyShardHandler(String shardUrl) {
        this.fullPath = shardUrl + REQUEST_PATH;
    }

    @Override
    public Response handleForKey(String key, Request request) {
        try {
            return switch (request.getMethod()) {
                case Request.METHOD_GET -> get(key);
                case Request.METHOD_PUT -> put(key, request.getBody());
                case Request.METHOD_DELETE -> delete(key);
                default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            };
        } catch (HttpTimeoutException e) {
            return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
        } catch (Exception e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    private HttpRequest.Builder requestForKey(String key) {
        return HttpRequest.newBuilder(URI.create(fullPath.concat(key))).timeout(TIMEOUT);
    }

    private Response get(String key) throws IOException, InterruptedException {
        return send(requestForKey(key).GET().build());
    }

    private Response put(String key, byte[] body) throws IOException, InterruptedException {
        return send(requestForKey(key).PUT(HttpRequest.BodyPublishers.ofByteArray(body)).build());
    }

    private Response delete(String key) throws IOException, InterruptedException {
        return send(requestForKey(key).DELETE().build());
    }

    private static Response send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        return new Response(String.valueOf(response.statusCode()), response.body());
    }

    @Override
    public void close() throws IOException {
    }
}
