package ok.dht.test.mikhaylov.internal;

import ok.dht.test.mikhaylov.MyService;
import one.nio.http.Request;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class JavaHttpClient extends InternalHttpClient {

    private HttpClient client;

    private static final Logger logger = LoggerFactory.getLogger(JavaHttpClient.class);

    public JavaHttpClient() {
        super();
        client = HttpClient.newBuilder()
                .executor(getExecutor())
                .connectTimeout(Duration.ofSeconds(1)) // internal services should be fast
                .build();
    }

    @Override
    public Response proxyRequest(Request request, String shard) throws ExecutionException, InterruptedException {
        byte[] body = request.getBody();
        HttpRequest.BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody() :
                HttpRequest.BodyPublishers.ofByteArray(body);
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(shard + MyService.convertPathToInternal(request.getURI())))
                .method(request.getMethodName(), publisher).build();
        try {
            return client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray())
                    .handleAsync((response, throwable) -> {
                        if (throwable != null) {
                            return MyService.makeError(logger, shard, throwable);
                        }
                        return new Response(
                                responseCodeToStatusText(response.statusCode()),
                                response.body()
                        );
                    }).get(1, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return MyService.makeError(logger, shard, e);
        }
    }

    private static String responseCodeToStatusText(int code) {
        return switch (code) {
            case HttpURLConnection.HTTP_OK ->Response.OK;
            case HttpURLConnection.HTTP_CREATED ->Response.CREATED;
            case HttpURLConnection.HTTP_ACCEPTED ->Response.ACCEPTED;
            case HttpURLConnection.HTTP_NO_CONTENT ->Response.NO_CONTENT;
            case HttpURLConnection.HTTP_SEE_OTHER ->Response.SEE_OTHER;
            case HttpURLConnection.HTTP_NOT_MODIFIED ->Response.NOT_MODIFIED;
            case HttpURLConnection.HTTP_USE_PROXY ->Response.USE_PROXY;
            case HttpURLConnection.HTTP_BAD_REQUEST ->Response.BAD_REQUEST;
            case HttpURLConnection.HTTP_UNAUTHORIZED ->Response.UNAUTHORIZED;
            case HttpURLConnection.HTTP_PAYMENT_REQUIRED ->Response.PAYMENT_REQUIRED;
            case HttpURLConnection.HTTP_FORBIDDEN ->Response.FORBIDDEN;
            case HttpURLConnection.HTTP_NOT_FOUND ->Response.NOT_FOUND;
            case HttpURLConnection.HTTP_NOT_ACCEPTABLE ->Response.NOT_ACCEPTABLE;
            case HttpURLConnection.HTTP_CONFLICT ->Response.CONFLICT;
            case HttpURLConnection.HTTP_GONE ->Response.GONE;
            case HttpURLConnection.HTTP_LENGTH_REQUIRED ->Response.LENGTH_REQUIRED;
            case HttpURLConnection.HTTP_INTERNAL_ERROR ->Response.INTERNAL_ERROR;
            case HttpURLConnection.HTTP_NOT_IMPLEMENTED ->Response.NOT_IMPLEMENTED;
            case HttpURLConnection.HTTP_BAD_GATEWAY ->Response.BAD_GATEWAY;
            case HttpURLConnection.HTTP_GATEWAY_TIMEOUT ->Response.GATEWAY_TIMEOUT;
            default -> throw new IllegalArgumentException("Unknown response code: " + code);
        };
    }

    @Override
    public void close() throws IOException {
        super.close();
        client = null;
    }
}
