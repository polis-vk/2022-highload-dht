package ok.dht.test.kurdyukov.client;

import one.nio.http.Request;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class HttpClientDao {
    private static final int THREAD_POOL_SIZE = 3;
    private static final HttpClient httpClient = HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(1))
            .executor(
                    new ThreadPoolExecutor(
                            THREAD_POOL_SIZE,
                            THREAD_POOL_SIZE,
                            0,
                            TimeUnit.MILLISECONDS,
                            new ArrayBlockingQueue<>(THREAD_POOL_SIZE * 10)
                    )
            )
            .build();

    private HttpClientDao() {

    }

    public static CompletableFuture<HttpResponse<byte[]>> requestNode(
            String uri,
            int method,
            Request request
    ) throws URISyntaxException {
        HttpRequest.Builder builder = HttpRequest
                .newBuilder(new URI(uri));

        HttpRequest httpRequest = switch (method) {
            case Request.METHOD_GET -> builder.GET().build();
            case Request.METHOD_PUT -> builder.PUT(HttpRequest.BodyPublishers.ofByteArray(request.getBody())).build();
            case Request.METHOD_DELETE -> builder.DELETE().build();
            default -> throw new IllegalArgumentException("No support htt method!");
        };

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
    }
}
