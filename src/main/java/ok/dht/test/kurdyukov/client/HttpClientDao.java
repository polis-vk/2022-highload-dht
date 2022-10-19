package ok.dht.test.kurdyukov.client;

import ok.dht.test.kurdyukov.server.HttpServerDao;
import one.nio.http.Request;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class HttpClientDao {
    public final AtomicBoolean isNotConnect = new AtomicBoolean(false);

    private final ScheduledExecutorService listenerConnect = Executors.newSingleThreadScheduledExecutor();

    public HttpClientDao(String uri) {
        try {
            HttpRequest httpRequest = HttpRequest
                    .newBuilder(new URI(uri + HttpServerDao.PING))
                    .GET()
                    .build();

            listenerConnect.schedule(
                    () -> {
                        try {
                            HttpResponse<byte[]> response = httpClient
                                    .send(
                                            httpRequest,
                                            HttpResponse.BodyHandlers.ofByteArray()
                                    );

                            if (response.statusCode() == 200) {
                                isNotConnect.set(true);
                            }
                        } catch (Exception e) {
                            isNotConnect.set(false);
                        }
                    },
                    500,
                    TimeUnit.MILLISECONDS
            );
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private final HttpClient httpClient = HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(1))
            .build();

    public CompletableFuture<HttpResponse<byte[]>> requestNode(
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

    public void close() {
        listenerConnect.shutdown();
    }
}
