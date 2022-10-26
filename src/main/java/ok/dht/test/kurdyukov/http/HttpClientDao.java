package ok.dht.test.kurdyukov.http;

import one.nio.http.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class HttpClientDao {

    private static final Logger logger = LoggerFactory.getLogger(HttpClientDao.class);

    public static final String TIMESTAMP_HEADER = "TIMESTAMP_HEADER";
    public static final String CLUSTER_HEADER = "CLUSTER_HEADER";

    private final ScheduledExecutorService listenerConnect = Executors.newSingleThreadScheduledExecutor();

    private final HttpClient httpClient = HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(1))
            .build();

    public CompletableFuture<HttpResponse<byte[]>> requestNode(
            String uri,
            int method,
            Instant timestamp,
            byte[] value
    ) {
        HttpRequest.Builder builder;
        try {
            builder = HttpRequest
                    .newBuilder(new URI(uri))
                    .setHeader(CLUSTER_HEADER, uri)
                    .setHeader(TIMESTAMP_HEADER, timestamp.toString());
        } catch (URISyntaxException e) {
            logger.error("Fail URI syntax with uri: " + uri, e);

            throw new RuntimeException(e);
        }

        HttpRequest httpRequest = switch (method) {
            case Request.METHOD_GET -> builder.GET().build();
            case Request.METHOD_PUT -> builder.PUT(HttpRequest
                            .BodyPublishers
                            .ofByteArray(value))
                    .build();
            case Request.METHOD_DELETE -> builder.DELETE().build();
            default -> throw new IllegalArgumentException("No support htt method!");
        };

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
    }

    public void close() {
        listenerConnect.shutdown();
    }
}
