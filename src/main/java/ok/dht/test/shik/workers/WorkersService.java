package ok.dht.test.shik.workers;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class WorkersService {

    private static final int TIMEOUT_MILLIS = 10;

    private ExecutorService pool;
    private final WorkersConfig config;

    public WorkersService(WorkersConfig config) {
        this.config = config;
    }

    public void start() {
        RejectedExecutionHandler rejectedHandler = config.getQueuePolicy() == WorkersConfig.QueuePolicy.FIFO
            ? new ThreadPoolExecutor.DiscardPolicy()
            : new ThreadPoolExecutor.DiscardOldestPolicy();

        pool = new ThreadPoolExecutor(config.getCorePoolSize(), config.getMaxPoolSize(),
            config.getKeepAliveTime(), config.getUnit(), new ArrayBlockingQueue<>(config.getQueueCapacity()),
            Executors.defaultThreadFactory(), rejectedHandler);
    }

    public List<Runnable> stop() {
        pool.shutdown();
        try {
            return pool.awaitTermination(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                ? Collections.emptyList()
                : pool.shutdownNow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return pool.shutdownNow();
        }
    }

    public void submitTask(Runnable runnable) {
         pool.submit(runnable);
    }
}
