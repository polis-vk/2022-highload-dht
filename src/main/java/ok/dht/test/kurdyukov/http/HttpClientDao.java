package ok.dht.test.kurdyukov.http;

import ok.dht.test.kurdyukov.dao.model.DaoEntry;
import ok.dht.test.kurdyukov.utils.ObjectMapper;
import one.nio.http.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class HttpClientDao {

    private static final Logger logger = LoggerFactory.getLogger(HttpClientDao.class);

    public static final String HEADER_NAME = "HEADER_FROM_CLUSTER";

    private final ScheduledExecutorService listenerConnect = Executors.newSingleThreadScheduledExecutor();

    private final HttpClient httpClient = HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(1))
            .build();

    public CompletableFuture<HttpResponse<byte[]>> requestNode(
            String uri,
            int method,
            DaoEntry entry
    ) {
        HttpRequest.Builder builder;
        try {
            builder = HttpRequest
                    .newBuilder(new URI(uri))
                    .setHeader(HEADER_NAME, uri);
        } catch (URISyntaxException e) {
            logger.error("Fail URI syntax with uri: " + uri, e);

            throw new RuntimeException(e);
        }

        HttpRequest httpRequest = switch (method) {
            case Request.METHOD_GET -> builder.GET().build();
            case Request.METHOD_PUT -> {
                try {
                    yield builder.PUT(HttpRequest
                            .BodyPublishers
                            .ofByteArray(ObjectMapper.serialize(entry)))
                            .build();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            case Request.METHOD_DELETE -> builder.DELETE().build();
            default -> throw new IllegalArgumentException("No support htt method!");
        };

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
    }

    public void close() {
        listenerConnect.shutdown();
    }
}
