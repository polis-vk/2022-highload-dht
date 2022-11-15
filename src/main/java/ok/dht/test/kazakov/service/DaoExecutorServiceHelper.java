package ok.dht.test.kazakov.service;

import ok.dht.test.kazakov.service.http.DaoHttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class DaoExecutorServiceHelper {

    private static final Logger LOG = LoggerFactory.getLogger(DaoExecutorServiceHelper.class);

    private DaoExecutorServiceHelper() {
        // no operations
    }

    public static ExecutorService createAbortThreadPool(final int poolSize,
                                                        final int queueCapacity) {
        return createExecutorService(poolSize, queueCapacity, new ThreadPoolExecutor.AbortPolicy());
    }

    public static ExecutorService createDiscardOldestThreadPool(final int poolSize,
                                                                final int queueCapacity) {
        return createExecutorService(poolSize, queueCapacity, new DiscardOldestRejectionPolicy());
    }

    private static ExecutorService createExecutorService(final int poolSize,
                                                         final int queueCapacity,
                                                         final RejectedExecutionHandler rejectedExecutionHandler) {
        return new ThreadPoolExecutor(poolSize,
                poolSize,
                0L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new DaoAsyncExecutorThreadFactory(),
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

        private final AtomicInteger threadsCreated = new AtomicInteger(1);

        @Override
        public Thread newThread(@Nonnull final Runnable target) {
            final Thread thread = new Thread(target, "DaoAsyncExecutor#" + threadsCreated.getAndIncrement());
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
