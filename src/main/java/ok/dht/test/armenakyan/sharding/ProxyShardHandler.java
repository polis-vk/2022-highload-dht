package ok.dht.test.armenakyan.sharding;

import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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
            return handleAsync(key, request).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
        } catch (ExecutionException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    @Override
    public void handleForKey(String key, Request request, HttpSession session) throws IOException {
        handleAsync(key, request).thenAcceptAsync(response -> {
            try {
                session.sendResponse(response);
            } catch (IOException e) {
                session.close();
            }
        });
    }

    private CompletableFuture<Response> handleAsync(String key, Request request) {
        HttpRequest proxyRequest = HttpRequest.newBuilder(URI.create(fullPath.concat(key)))
                .timeout(TIMEOUT)
                .method(request.getMethodName(), HttpRequest.BodyPublishers.ofByteArray(request.getBody()))
                .build();
        return HTTP_CLIENT
                .sendAsync(proxyRequest, HttpResponse.BodyHandlers.ofByteArray())
                .handleAsync((resp, ex) -> {
                    if (ex == null) {
                        return new Response(String.valueOf(resp.statusCode()), resp.body());
                    }

                    if (ex instanceof HttpTimeoutException) {
                        return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
                    } else {
                        return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
                    }
                });
    }
}
