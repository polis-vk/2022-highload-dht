package ok.dht.test.zhidkov.services;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class RequestExecutorService {

    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = 1;
    private static final int KEEP_ALIVE_TIME = 1;
    private final ExecutorService executorService;

    public RequestExecutorService(int queueSize, RejectedExecutionHandler handler) {
        this.executorService = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                THREAD_POOL_SIZE,
                KEEP_ALIVE_TIME,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueSize),
                r -> new Thread(r, "RequestExecutorServiceThread " + Thread.currentThread().getId()),
                handler
        );
    }

    public void submitTask(Runnable r) {
        executorService.submit(r);
    }

    public void shutdown() {
        executorService.shutdown();
        boolean isTerminated = false;
        while (!isTerminated) {
            try {
                isTerminated = executorService.awaitTermination(1, TimeUnit.HOURS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }
}
