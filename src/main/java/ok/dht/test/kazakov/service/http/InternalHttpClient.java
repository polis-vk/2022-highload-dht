package ok.dht.test.kazakov.service.http;

import ok.dht.test.kazakov.service.sharding.Shard;
import ok.dht.test.kazakov.service.ws.InternalDaoWebService;
import one.nio.http.Request;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class InternalHttpClient {

    private static final Logger LOG = LoggerFactory.getLogger(InternalHttpClient.class);

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
                                                                           @Nonnull final Shard shard,
                                                                           final long timestamp,
                                                                           final long newEntryTimestamp) {
        return client.sendAsync(
                buildDaoShardRequest(request, entityId, shard, timestamp, newEntryTimestamp),
                HttpResponse.BodyHandlers.ofByteArray()
        );
    }

    private HttpRequest buildDaoShardRequest(final Request request,
                                             final String id,
                                             final Shard shard,
                                             final long timestamp,
                                             final long newEntryTimestamp) {
        final URI uri = URI.create(
                shard.getUrl()
                        + InternalDaoWebService.INTERNAL_ENTITY_API_PATH
                        + "?id="
                        + id
                        + "&timestamp="
                        + timestamp
                        + "&newEntryTimestamp="
                        + newEntryTimestamp
        );
        LOG.debug("Sending internal request {} {}", DaoHttpServer.METHODS.get(request.getMethod()), uri);

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

    // very big probability that this code will be the same as in other implementations
    @SuppressWarnings("DuplicatedCode")
    public static String convertHttpStatusCode(final int statusCode) {
        return switch (statusCode) {
            case HttpURLConnection.HTTP_OK -> Response.OK;
            case HttpURLConnection.HTTP_CREATED -> Response.CREATED;
            case HttpURLConnection.HTTP_ACCEPTED -> Response.ACCEPTED;
            case HttpURLConnection.HTTP_NOT_AUTHORITATIVE -> Response.NON_AUTHORITATIVE_INFORMATION;
            case HttpURLConnection.HTTP_NO_CONTENT -> Response.NO_CONTENT;
            case HttpURLConnection.HTTP_RESET -> Response.RESET_CONTENT;
            case HttpURLConnection.HTTP_PARTIAL -> Response.PARTIAL_CONTENT;
            case HttpURLConnection.HTTP_MULT_CHOICE -> Response.MULTIPLE_CHOICES;
            case HttpURLConnection.HTTP_MOVED_PERM -> Response.MOVED_PERMANENTLY;
            case HttpURLConnection.HTTP_MOVED_TEMP -> Response.FOUND;
            case HttpURLConnection.HTTP_SEE_OTHER -> Response.SEE_OTHER;
            case HttpURLConnection.HTTP_NOT_MODIFIED -> Response.NOT_MODIFIED;
            case HttpURLConnection.HTTP_USE_PROXY -> Response.USE_PROXY;
            case HttpURLConnection.HTTP_BAD_REQUEST -> Response.BAD_REQUEST;
            case HttpURLConnection.HTTP_UNAUTHORIZED -> Response.UNAUTHORIZED;
            case HttpURLConnection.HTTP_PAYMENT_REQUIRED -> Response.PAYMENT_REQUIRED;
            case HttpURLConnection.HTTP_FORBIDDEN -> Response.FORBIDDEN;
            case HttpURLConnection.HTTP_NOT_FOUND -> Response.NOT_FOUND;
            case HttpURLConnection.HTTP_BAD_METHOD -> Response.METHOD_NOT_ALLOWED;
            case HttpURLConnection.HTTP_PROXY_AUTH -> Response.PROXY_AUTHENTICATION_REQUIRED;
            case HttpURLConnection.HTTP_CLIENT_TIMEOUT -> Response.REQUEST_TIMEOUT;
            case HttpURLConnection.HTTP_CONFLICT -> Response.CONFLICT;
            case HttpURLConnection.HTTP_GONE -> Response.GONE;
            case HttpURLConnection.HTTP_LENGTH_REQUIRED -> Response.LENGTH_REQUIRED;
            case HttpURLConnection.HTTP_PRECON_FAILED -> Response.PRECONDITION_FAILED;
            case HttpURLConnection.HTTP_ENTITY_TOO_LARGE -> Response.REQUEST_ENTITY_TOO_LARGE;
            case HttpURLConnection.HTTP_REQ_TOO_LONG -> Response.REQUEST_URI_TOO_LONG;
            case HttpURLConnection.HTTP_UNSUPPORTED_TYPE -> Response.UNSUPPORTED_MEDIA_TYPE;
            case HttpURLConnection.HTTP_INTERNAL_ERROR -> Response.INTERNAL_ERROR;
            case HttpURLConnection.HTTP_NOT_IMPLEMENTED -> Response.NOT_IMPLEMENTED;
            case HttpURLConnection.HTTP_BAD_GATEWAY -> Response.BAD_GATEWAY;
            case HttpURLConnection.HTTP_UNAVAILABLE -> Response.SERVICE_UNAVAILABLE;
            case HttpURLConnection.HTTP_GATEWAY_TIMEOUT -> Response.GATEWAY_TIMEOUT;
            case HttpURLConnection.HTTP_VERSION -> Response.HTTP_VERSION_NOT_SUPPORTED;
            default -> throw new IllegalArgumentException("Unsupported http code " + statusCode);
        };
    }
}
