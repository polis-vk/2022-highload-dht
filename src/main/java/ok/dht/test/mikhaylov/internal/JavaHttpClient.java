package ok.dht.test.mikhaylov.internal;

import ok.dht.test.mikhaylov.MyService;
import one.nio.http.Request;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutionException;

public class JavaHttpClient extends InternalHttpClient {

    private HttpClient client; // todo: try to use one.nio.http.HttpClient

    // Available in Request.METHODS, but not accessible
    private static final String[] METHODS = new String[]{
            "",
            "GET",
            "POST",
            "HEAD",
            "OPTIONS",
            "PUT",
            "DELETE",
            "TRACE",
            "CONNECT",
            "PATCH"
    };

    private static final Logger logger = LoggerFactory.getLogger(JavaHttpClient.class);

    public JavaHttpClient() {
        super();
        client = HttpClient.newBuilder()
                .executor(getExecutor())
                .connectTimeout(Duration.ofSeconds(1)) // internal services should be fast
                .build();
    }

    @Override
    public Response proxyRequest(Request request, String shard) {
        try {
            return client.sendAsync(
                            HttpRequest.newBuilder()
                                    .uri(URI.create(shard + request.getURI().replaceFirst(MyService.ENTITY_PATH,
                                            MyService.ENTITY_INTERNAL_PATH)))
                                    .method(METHODS[request.getMethod()],
                                            HttpRequest.BodyPublishers.ofByteArray(request.getBody()))
                                    .build(),
                            HttpResponse.BodyHandlers.ofByteArray()
                    )
                    .handleAsync((response, throwable) -> {
                        if (throwable != null) {
                            logger.error("Could not proxy request to {}", shard, throwable);
                            return new Response(Response.INTERNAL_ERROR, new byte[0]);
                        }
                        return new Response(
                                Integer.toString(response.statusCode()), // status text isn't necessary
                                response.body()
                        );
                    }).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            logger.error("Could not proxy request to {}", shard, e);
            return new Response(Response.INTERNAL_ERROR, new byte[0]);
        }
    }

    @Override
    public void close() throws IOException {
        super.close();
        client = null;
    }
}
