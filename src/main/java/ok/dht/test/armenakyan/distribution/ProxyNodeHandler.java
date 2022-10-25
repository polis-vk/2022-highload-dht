package ok.dht.test.armenakyan.distribution;

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
    private static final int MAX_QUEUE_SIZE = 5000;
    private static final int MAX_POOL_WORKERS = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_WORKERS = Math.min(2, MAX_POOL_WORKERS);
    private static final long KEEP_ALIVE_TIME_SEC = 3;
    private final ExecutorService clientPool;
    private final HttpClient httpClient;
    private final String fullPath;

    public ProxyNodeHandler(String nodeUrl) {
        this.fullPath = nodeUrl + REQUEST_PATH;
        this.clientPool = new ThreadPoolExecutor(
                CORE_POOL_WORKERS, MAX_POOL_WORKERS,
                KEEP_ALIVE_TIME_SEC, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(MAX_QUEUE_SIZE)
        );
        this.httpClient = HttpClient.newBuilder()
                .executor(clientPool)
                .build();
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
        }, clientPool);
    }

    private CompletableFuture<Response> handleAsync(String key, Request request) {
        HttpRequest proxyRequest = HttpRequest.newBuilder(URI.create(fullPath.concat(key)))
                .timeout(TIMEOUT)
                .method(request.getMethodName(), HttpRequest.BodyPublishers.ofByteArray(request.getBody()))
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
