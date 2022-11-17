package ok.dht.test.shakhov.concurrent;

import one.nio.async.CustomThreadFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class DefaultThreadPoolManager {
    private static final int DEFAULT_POOL_SIZE = Runtime.getRuntime().availableProcessors() / 2;
    private static final int AWAIT_TERMINATION_TIMEOUT_SECONDS = 20;
    private static final int QUEUE_MAX_SIZE = 10_000;

    private static final AtomicInteger nextThreadPoolId = new AtomicInteger(0);

    private DefaultThreadPoolManager() {
    }

    public static ThreadPoolExecutor createThreadPool(String name) {
        return createThreadPool(name, DEFAULT_POOL_SIZE);
    }

    public static ThreadPoolExecutor createThreadPool(String name, int poolSize) {
        poolSize = Math.max(poolSize, 1);
        int threadPoolId = nextThreadPoolId.incrementAndGet();
        String threadPoolFullName = name + "-" + threadPoolId;
        ThreadFactory namedThreadFactory = new CustomThreadFactory(threadPoolFullName);
        RejectedExecutionHandler loggableRejectedExecutionHandler =
                new LoggableRejectedExecutionHandler(threadPoolFullName);

        return new ThreadPoolExecutor(
                poolSize,
                poolSize,
                0L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_MAX_SIZE),
                namedThreadFactory,
                loggableRejectedExecutionHandler
        );
    }

    public static void shutdownThreadPool(ThreadPoolExecutor threadPool) {
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(AWAIT_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
                threadPool.awaitTermination(AWAIT_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
