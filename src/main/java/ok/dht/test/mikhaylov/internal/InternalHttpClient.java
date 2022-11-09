package ok.dht.test.mikhaylov.internal;

import one.nio.http.Request;
import one.nio.http.Response;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public abstract class InternalHttpClient implements Closeable {

    private static final int NUM_THREADS = 4;

    private static final int MAX_REQUESTS = 2048;

    private final ExecutorService executor;

    protected InternalHttpClient() {
        executor = new ThreadPoolExecutor(
                NUM_THREADS,
                NUM_THREADS,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingDeque<>(MAX_REQUESTS)
        );
    }

    public abstract CompletableFuture<Response> proxyRequest(Request request, String shard);

    protected ExecutorService getExecutor() {
        return executor;
    }

    @Override
    public void close() throws IOException {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
