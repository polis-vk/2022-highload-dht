package ok.dht.test.galeev;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SkipOldExecutorFactory {
    protected static final int CORE_POLL_SIZE = 64;
    protected static final int KEEP_ALIVE_TIME = 0;
    protected static final int QUEUE_CAPACITY = 128;
    protected static final RejectedExecutionHandler defaultHandler = (newRunnable, e) -> {
        if (!e.isShutdown()) {
            //
            Runnable runnable = e.getQueue().poll();
            if (runnable instanceof CustomHttpServer.RunnableForRequestHandler) {
                ((CustomHttpServer.RunnableForRequestHandler) runnable).rejectRequest();
            }
            e.execute(newRunnable);
        }
    };

    private final AtomicInteger amountOfThreads = new AtomicInteger(0);
    protected final ThreadFactory threadFactory = r
            -> new Thread(r, "ServiceExecutor#" + amountOfThreads.getAndIncrement());

    public ThreadPoolExecutor getExecutor() {
        return new ThreadPoolExecutor(
                CORE_POLL_SIZE,
                CORE_POLL_SIZE,
                KEEP_ALIVE_TIME,
                TimeUnit.MINUTES,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                threadFactory,
                defaultHandler
        );
    }

    public static void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                pool.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!pool.awaitTermination(60, TimeUnit.SECONDS))
                    System.err.println("Pool did not terminate");
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }
}
