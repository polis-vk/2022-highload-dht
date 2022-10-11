package ok.dht.test.mikhaylov;

import one.nio.http.Request;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MyInternalHttpClient implements Closeable {

    private HttpClient client; // todo: try to use one.nio.http.HttpClient

    private final ExecutorService executor;

    private static final int NUM_THREADS = 4; // todo: tune

    private static final int MAX_REQUESTS = 128;

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

    public MyInternalHttpClient() {
        executor = new ThreadPoolExecutor(
                NUM_THREADS,
                NUM_THREADS,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingDeque<>(MAX_REQUESTS)
        );
        client = HttpClient.newBuilder()
                .executor(executor)
                .connectTimeout(Duration.ofSeconds(1)) // internal services should be fast
                .build();
    }

    public CompletableFuture<HttpResponse<byte[]>> proxyRequest(Request request, String shard) {
         return client.sendAsync(
                HttpRequest.newBuilder()
                        .uri(URI.create(shard + request.getURI().replaceFirst(MyService.ENTITY_PATH,
                                MyService.ENTITY_INTERNAL_PATH)))
                        .method(METHODS[request.getMethod()], HttpRequest.BodyPublishers.ofByteArray(request.getBody()))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray()
         );
    }

    @Override
    public void close() throws IOException {
        executor.shutdownNow();
        client = null;
    }
}
