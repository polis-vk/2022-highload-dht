package ok.dht.test.galeev;

import one.nio.http.Response;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ClusterClient {
    public static final int deadNodeThreshold = 10;
    private static final int CONNECT_TIMEOUT = 150;
    private static final Duration CLIENT_TIMEOUT = Duration.of(300, ChronoUnit.MILLIS);
    private final HttpClient httpClient;
    private final ExecutorService executor;

    public ClusterClient() throws IOException {
        executor = Executors.newFixedThreadPool(8);
        httpClient = HttpClient.newBuilder()
                .connectTimeout(CLIENT_TIMEOUT)
                .executor(executor)
                .build();
    }

    public void stop() throws IOException {
        executor.shutdown();
    }

    public Response get(ConsistentHashRouter.Node routerNode, String id)
            throws ExecutionException, InterruptedException, TimeoutException {
        HttpResponse<byte[]> httpResponse = httpClient.sendAsync(
                requestBuilderForKey(routerNode.nodeAddress, id).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()
        ).get(
                CONNECT_TIMEOUT,
                TimeUnit.SECONDS
        );
        return convertToResponse(httpResponse);
    }

    public Response put(ConsistentHashRouter.Node routerNode, String id, byte[] body)
            throws ExecutionException, InterruptedException, TimeoutException {
        try {
            HttpResponse<byte[]> httpResponse = httpClient.sendAsync(
                    requestBuilderForKey(routerNode.nodeAddress, id).PUT(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            ).get(
                    CONNECT_TIMEOUT,
                    TimeUnit.MILLISECONDS
            );
            return convertToResponse(httpResponse);
        } catch (TimeoutException e) {
            if (routerNode.errorCount.incrementAndGet() > deadNodeThreshold) {
                routerNode.isAlive = false;
            }
            throw e;
        }
    }

    public Response delete(ConsistentHashRouter.Node routerNode, String id)
            throws ExecutionException, InterruptedException, TimeoutException {
        HttpResponse<byte[]> httpResponse = httpClient.sendAsync(
                requestBuilderForKey(routerNode.nodeAddress, id).DELETE().build(),
                HttpResponse.BodyHandlers.ofByteArray()
        ).get(
                CONNECT_TIMEOUT,
                TimeUnit.MILLISECONDS
        );
        return convertToResponse(httpResponse);
    }

    private static Response convertToResponse(HttpResponse<byte[]> httpResponse) {
        return switch (httpResponse.statusCode()) {
            case HttpURLConnection.HTTP_OK -> new Response(Response.OK, httpResponse.body());
            case HttpURLConnection.HTTP_NOT_FOUND -> new Response(Response.NOT_FOUND, Response.EMPTY);
            case HttpURLConnection.HTTP_ACCEPTED -> new Response(Response.ACCEPTED, Response.EMPTY);
            case HttpURLConnection.HTTP_CREATED -> new Response(Response.CREATED, Response.EMPTY);
            default -> new Response(String.valueOf(httpResponse.statusCode()), Response.EMPTY);
        };
    }

    private static HttpRequest.Builder requestBuilder(String uri, String path) {
        return HttpRequest.newBuilder(URI.create(uri + path));
    }

    private static HttpRequest.Builder requestBuilderForKey(String uri, String key) {
        return requestBuilder(uri, "/v0/entity?id=" + key);
    }
}
