package ok.dht.test.mikhaylov.internal;

import one.nio.http.Request;
import one.nio.http.Response;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class InternalHttpClient implements Closeable {

    private static final int NUM_THREADS = 4;

    private static final int MAX_REQUESTS = 128;

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

    @Nullable
    public abstract Response proxyRequest(Request request, String shard) throws InterruptedException,
            ExecutionException, TimeoutException;

    protected ExecutorService getExecutor() {
        return executor;
    }

    @Override
    public void close() throws IOException {
        executor.shutdownNow();
    }
}
