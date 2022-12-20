package ok.dht.test.kazakov.service.http;

import ok.dht.test.kazakov.service.DaoWebService;
import ok.dht.test.kazakov.service.sharding.Shard;
import one.nio.http.Request;

import javax.annotation.Nonnull;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class InternalHttpClient {

    private static final Duration INTERNAL_API_RESPONSE_TIMEOUT = Duration.ofSeconds(2);

    private final HttpClient client;
    private final Executor requestExecutor;

    public InternalHttpClient(@Nonnull final Executor requestExecutor) {
        this.client = HttpClient
                .newBuilder()
                .executor(requestExecutor)
                .build();
        this.requestExecutor = requestExecutor;
    }

    public Executor getRequestExecutor() {
        return requestExecutor;
    }

    public CompletableFuture<HttpResponse<byte[]>> resendDaoRequestToShard(@Nonnull final Request request,
                                                                           @Nonnull final String entityId,
                                                                           @Nonnull final Shard shard) {
        return client.sendAsync(
                buildDaoShardRequest(request, entityId, shard),
                HttpResponse.BodyHandlers.ofByteArray()
        );
    }

    private HttpRequest buildDaoShardRequest(final Request request,
                                             final String id,
                                             final Shard shard) {
        final URI uri = URI.create(shard.getUrl() + DaoWebService.INTERNAL_ENTITY_API_PATH + "?id=" + id);
        final HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(INTERNAL_API_RESPONSE_TIMEOUT);

        switch (request.getMethod()) {
            case Request.METHOD_GET -> builder.GET();
            case Request.METHOD_PUT -> builder.PUT(HttpRequest.BodyPublishers.ofByteArray(request.getBody()));
            case Request.METHOD_DELETE -> builder.DELETE();
            default -> throw new IllegalArgumentException(
                    "Unsupported method: " + DaoHttpServer.METHODS.get(request.getMethod())
            );
        }
        return builder.build();
    }
}
