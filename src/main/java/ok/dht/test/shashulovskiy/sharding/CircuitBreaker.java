package ok.dht.test.shashulovskiy.sharding;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerArray;

public class CircuitBreaker {

    private static final int FAILED_REQUESTS_SHUTDOWN_THRESHOLD = Math.max(5,
            Runtime.getRuntime().availableProcessors());
    private static final int RESTORE_ACTIVE_STATUS_TIMEOUT_DURATION = 10;
    private static final TimeUnit RESTORE_ACTIVE_STATUS_TIMEOUT_UNIT = TimeUnit.SECONDS;

    private final AtomicIntegerArray shardsFailedRequestsCount;
    private final AtomicBoolean[] isFailed;

    private final ScheduledExecutorService scheduler;

    public CircuitBreaker(int nodesCount) {
        this.shardsFailedRequestsCount = new AtomicIntegerArray(nodesCount);
        this.isFailed = new AtomicBoolean[nodesCount];
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        for (int i = 0; i < nodesCount; i++) {
            isFailed[i] = new AtomicBoolean(false);
        }
    }

    public void failOn(int node) {
        int currentFailCounter = shardsFailedRequestsCount.incrementAndGet(node);
        if (currentFailCounter >= FAILED_REQUESTS_SHUTDOWN_THRESHOLD) {
            isFailed[node].set(true);

            scheduler.schedule(() -> {
                shardsFailedRequestsCount.set(node, 0);
                isFailed[node].set(false);
            }, RESTORE_ACTIVE_STATUS_TIMEOUT_DURATION, RESTORE_ACTIVE_STATUS_TIMEOUT_UNIT);
        }
    }

    public void successOn(int node) {
        shardsFailedRequestsCount.getAndAccumulate(node, -1, (prev, x) -> Integer.max(0, prev + x));
    }

    public boolean isActive(int node) {
        return !isFailed[node].get();
    }
}
