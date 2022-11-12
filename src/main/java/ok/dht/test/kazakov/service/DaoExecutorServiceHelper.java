package ok.dht.test.kazakov.service;

import ok.dht.test.kazakov.service.http.DaoHttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class DaoExecutorServiceHelper {

    private static final Logger LOG = LoggerFactory.getLogger(DaoExecutorServiceHelper.class);

    private static final int GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS = 60;

    private DaoExecutorServiceHelper() {
        // no operations
    }

    public static void shutdownGracefully(final ExecutorService executorService) throws InterruptedException {
        executorService.shutdown();
        if (!executorService.awaitTermination(GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            LOG.error(
                    "Could not gracefully terminate {} in {} seconds, terminating all pending tasks",
                    executorService,
                    GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS
            );

            executorService.shutdownNow();
            while (!executorService.awaitTermination(GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                LOG.error(
                        "Could not terminate {} in {} seconds, waiting for termination...",
                        executorService,
                        GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS
                );
            }
        }
    }

    public static ExecutorService createAbortThreadPool(final String threadName,
                                                        final int poolSize,
                                                        final int queueCapacity) {
        return createExecutorService(threadName, poolSize, queueCapacity, new ThreadPoolExecutor.AbortPolicy());
    }

    public static ExecutorService createDiscardOldestThreadPool(final String threadName,
                                                                final int poolSize,
                                                                final int queueCapacity) {
        return createExecutorService(threadName, poolSize, queueCapacity, new DiscardOldestRejectionPolicy());
    }

    private static ExecutorService createExecutorService(final String threadName,
                                                         final int poolSize,
                                                         final int queueCapacity,
                                                         final RejectedExecutionHandler rejectedExecutionHandler) {
        return new ThreadPoolExecutor(poolSize,
                poolSize,
                0L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new DaoAsyncExecutorThreadFactory(threadName),
                rejectedExecutionHandler);
    }

    private static final class DiscardOldestRejectionPolicy implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(final Runnable r,
                                      final ThreadPoolExecutor executor) {
            if (!executor.isShutdown()) {
                final Runnable discardedRunnable = executor.getQueue().poll();
                if (discardedRunnable instanceof DaoHttpServer.SynchronousRequestHandler) {
                    ((DaoHttpServer.SynchronousRequestHandler) discardedRunnable).rejectRequest();
                }

                executor.execute(r);
            }
        }
    }

    private static final class DaoAsyncExecutorThreadFactory implements ThreadFactory {

        private final String threadName;
        private final AtomicInteger threadsCreated = new AtomicInteger(1);

        private DaoAsyncExecutorThreadFactory(final String threadName) {
            this.threadName = threadName;
        }

        @Override
        public Thread newThread(@Nonnull final Runnable target) {
            final Thread thread = new Thread(target, threadName + "#" + threadsCreated.getAndIncrement());
            thread.setUncaughtExceptionHandler(new DaoAsyncExecutorUncaughtExceptionHandler());
            return thread;
        }
    }

    private static final class DaoAsyncExecutorUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

        @Override
        public void uncaughtException(final Thread t, final Throwable e) {
            LOG.error("Uncaught exception in thread {}", t.getName(), e);
        }
    }
}
