package ok.dht.test.kiselyov.util;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
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

public class InternalClient {
    private final HttpClient client;
    private static final Logger LOGGER = LoggerFactory.getLogger(InternalClient.class);

    public InternalClient(int poolSize) {
        HttpClient.Builder clientBuilder = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(100))
                .executor(Executors.newFixedThreadPool(poolSize,
                        new ThreadFactoryBuilder().setNameFormat("Thread-Internal-Client-%d").build()));
        client = clientBuilder.build();
    }

    public CompletableFuture<HttpResponse<byte[]>> sendRequestToNode(Request request, ClusterNode node, String id)
            throws URISyntaxException, IOException, InterruptedException {
        return client.sendAsync(resendRequest(request, node, id), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpRequest resendRequest(Request request, ClusterNode clusterNode, String id) throws URISyntaxException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(new URI(clusterNode.getUrl() + "/v0/entity?id=" + id));
        switch (request.getMethod()) {
            case Request.METHOD_GET -> builder.GET();
            case Request.METHOD_PUT -> builder.PUT(HttpRequest.BodyPublishers.ofByteArray(request.getBody()));
            case Request.METHOD_DELETE -> builder.DELETE();
            default -> {
                LOGGER.error("Unsupported request method: {}", request.getMethodName());
                throw new InternalError("Unsupported request method: " + request.getMethodName());
            }
        }
        if (request.getHeader("fromCoordinator") != null) {
            builder.setHeader("fromCoordinator", "1");
        }
        return builder.build();
    }
}
