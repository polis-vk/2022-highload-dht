package ok.dht.test.shik.workers;

import javax.annotation.Nonnull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
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
        BlockingQueue<Runnable> queue = config.getQueuePolicy() == WorkersConfig.QueuePolicy.LIFO
            ? new LinkedBlockingStack(config.getQueueCapacity())
            : new ArrayBlockingQueue<>(config.getQueueCapacity());

        pool = new ThreadPoolExecutor(config.getCorePoolSize(), config.getMaxPoolSize(),
            config.getKeepAliveTime(), config.getUnit(), queue);
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

    private static class LinkedBlockingStack extends LinkedBlockingDeque<Runnable> {

        public LinkedBlockingStack(int capacity) {
            super(capacity);
        }

        @Override
        public void put(@Nonnull Runnable e) throws InterruptedException {
            super.putFirst(e);
        }

        @Override
        public boolean offer(@Nonnull Runnable e) {
            return super.offerFirst(e);
        }

        @Override
        public boolean add(@Nonnull Runnable e) {
            super.addFirst(e);
            return true;
        }
    }
}
