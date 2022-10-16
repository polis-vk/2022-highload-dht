package ok.dht.test.kurdyukov.client;

import one.nio.http.Request;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class HttpClientDao {
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
}
