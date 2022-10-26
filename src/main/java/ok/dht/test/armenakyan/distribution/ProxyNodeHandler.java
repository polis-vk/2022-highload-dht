package ok.dht.test.armenakyan.distribution;

import ok.dht.test.armenakyan.util.ServiceUtils;
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
import java.util.concurrent.*;

public class ProxyNodeHandler implements NodeRequestHandler {
    private static final String REQUEST_PATH = "/v0/entity?id=";
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final int MAX_QUEUE_SIZE = 1000;
    private static final int MAX_POOL_WORKERS = Math.min(4, Runtime.getRuntime().availableProcessors());
    private static final int CORE_POOL_WORKERS = Math.min(2, MAX_POOL_WORKERS);
    private static final long KEEP_ALIVE_TIME_MS = 3000;
    private final ExecutorService clientPool;
    private final HttpClient httpClient;
    private final String fullPath;

    public ProxyNodeHandler(String nodeUrl) {
        this.fullPath = nodeUrl + REQUEST_PATH;
        this.clientPool = new ThreadPoolExecutor(
                CORE_POOL_WORKERS, MAX_POOL_WORKERS,
                KEEP_ALIVE_TIME_MS, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(MAX_QUEUE_SIZE)
        );
        this.httpClient = HttpClient.newBuilder()
                .executor(clientPool)
                .build();
    }

    @Override
    public void handleForKey(String key, Request request, HttpSession session, long timestamp) {
        handleAsync(key, request, timestamp).thenAcceptAsync(response -> {
            try {
                session.sendResponse(response);
            } catch (IOException e) {
                session.close();
            }
        }, clientPool);
    }

    @Override
    public CompletableFuture<Response> handleForKeyAsync(String key, Request request, long timestamp) {
        return handleAsync(key, request, timestamp);
    }

    private CompletableFuture<Response> handleAsync(String key, Request request, long timestamp) {
        HttpRequest proxyRequest = HttpRequest.newBuilder(URI.create(fullPath.concat(key)))
                .timeout(TIMEOUT)
                .header(ServiceUtils.TIMESTAMP_HEADER, String.valueOf(timestamp))
                .method(
                        request.getMethodName(),
                        request.getBody() != null
                        ? HttpRequest.BodyPublishers.ofByteArray(request.getBody())
                        : HttpRequest.BodyPublishers.noBody()
                )
                .build();
        return httpClient
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
                }, clientPool);
    }
}
