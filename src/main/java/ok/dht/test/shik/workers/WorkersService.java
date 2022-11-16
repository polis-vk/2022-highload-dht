package ok.dht.test.shik.workers;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class WorkersService {

    private static final Log LOG = LogFactory.getLog(WorkersService.class);
    private static final int TIMEOUT_MILLIS = 100;

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

    // В данный момент мой HttpServer не использует возвращаемое значение
    // Но глобально оно может иметь смысл, например, если критически важно eventually выполнить запросы,
    // то можно сохранить их в бд и исполнить при следующем старте сервера
    public List<Runnable> stop() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                List<Runnable> uncompletedTasks = pool.shutdownNow();
                if (!pool.awaitTermination(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    LOG.warn("Cannot terminate thread pool");
                }
                return uncompletedTasks;
            }

            return Collections.emptyList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return pool.shutdownNow();
        }
    }

    public void submitTask(Runnable runnable) {
         pool.submit(runnable);
    }
}
