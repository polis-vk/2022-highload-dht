package ok.dht.test.armenakyan.distribution;

import ok.dht.test.armenakyan.util.ServiceUtils;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ProxyNodeHandler implements NodeRequestHandler {
    private static final String REQUEST_PATH = "/v0/entity?id=";
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private final HttpClient httpClient;
    private final String fullPath;
    private final AtomicInteger availableTasks;

    public ProxyNodeHandler(String nodeUrl, HttpClient httpClient, int concurrentTaskLimit) {
        this.fullPath = nodeUrl + REQUEST_PATH;
        this.httpClient = httpClient;
        this.availableTasks = new AtomicInteger(concurrentTaskLimit);
    }

    @Override
    public void handleForKey(String key, Request request, HttpSession session, long timestamp) {
        try {
            session.sendResponse(handleAsync(key, request, timestamp).get());
        } catch (Exception e) {
            session.close();
        }
    }

    @Override
    public CompletableFuture<Response> handleForKeyAsync(String key, Request request, long timestamp) {
        return handleAsync(key, request, timestamp);
    }

    private CompletableFuture<Response> handleAsync(String key, Request request, long timestamp) {
        if (availableTasks.decrementAndGet() >= 0) {
            HttpRequest proxyRequest = HttpRequest.newBuilder(URI.create(fullPath.concat(key)))
                    .timeout(TIMEOUT)
                    .header(ServiceUtils.TIMESTAMP_HEADER, String.valueOf(timestamp))
                    .method(
                            request.getMethodName(),
                            request.getBody() == null
                                    ? HttpRequest.BodyPublishers.noBody()
                                    : HttpRequest.BodyPublishers.ofByteArray(request.getBody())
                    )
                    .build();
            return httpClient
                    .sendAsync(proxyRequest, HttpResponse.BodyHandlers.ofByteArray())
                    .handle((resp, ex) -> {
                        try {
                            if (ex == null) {
                                return new Response(String.valueOf(resp.statusCode()), resp.body());
                            }

                            if (ex instanceof HttpTimeoutException) {
                                return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
                            } else {
                                return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
                            }
                        } finally {
                            availableTasks.incrementAndGet();
                        }
                    });
        }

        availableTasks.incrementAndGet();
        return CompletableFuture.completedFuture(
                new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY)
        );
    }
}
