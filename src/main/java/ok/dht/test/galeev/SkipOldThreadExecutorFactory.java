package ok.dht.test.galeev;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SkipOldThreadExecutorFactory {

    protected static final int CORE_POLL_SIZE = 32;
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
}
