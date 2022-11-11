package ok.dht.test.kovalenko.dao.utils;

import java.io.Closeable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PoolKeeper implements Closeable {

    private final ExecutorService service;
    private final int shutdownTimeInSeconds;

    public PoolKeeper(int corePoolSize, int maxPoolSize, int queueCapacity) {
        this.service = new ThreadPoolExecutor(corePoolSize, maxPoolSize,
                60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new ThreadPoolExecutor.AbortPolicy());
        this.shutdownTimeInSeconds = 3 * 60;
    }

    @Override
    public void close() {
        this.service.shutdown();
        try {
            if (!service.awaitTermination((long) (2.0 / 3.0 * shutdownTimeInSeconds), TimeUnit.SECONDS)) {
                this.service.shutdownNow();
                if (!service.awaitTermination((long) (1.0 / 3.0 * shutdownTimeInSeconds), TimeUnit.SECONDS)) {
                    throw new RuntimeException("Unable to release thread pool");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("CheckReturnValue")
    public void submit(Runnable r) {
        this.service.submit(r);
    }

    public ExecutorService getService() {
        return service;
    }
}
