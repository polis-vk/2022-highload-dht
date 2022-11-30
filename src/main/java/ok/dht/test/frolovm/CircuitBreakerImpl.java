package ok.dht.test.frolovm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.IntBinaryOperator;

public class CircuitBreakerImpl implements CircuitBreaker {

    private static final int RETRY_TIME_PERIOD = 5000;
    private static final IntBinaryOperator SAFE_DECREMENT_FUNC = (value, term) -> Math.max(0, term + value);
    private final AtomicIntegerArray failed;
    private final int maxRequestTries;
    private final Map<String, Integer> nameToIndex;
    private final AtomicBoolean[] isRestarterStart;

    private final ScheduledExecutorService demonRestarter;

    public CircuitBreakerImpl(int maxRequestTries, List<String> shardNames) {
        this.failed = new AtomicIntegerArray(shardNames.size());
        this.maxRequestTries = maxRequestTries;
        this.nameToIndex = new HashMap<>();
        this.isRestarterStart = new AtomicBoolean[shardNames.size()];
        for (int i = 0; i < shardNames.size(); ++i) {
            this.nameToIndex.put(shardNames.get(i), i);
            isRestarterStart[i] = new AtomicBoolean(false);
        }
        this.demonRestarter = Executors.newScheduledThreadPool(1);
    }

    @Override
    public boolean isReady(String shardName) {
        int index = nameToIndex.get(shardName);
        return maxRequestTries > failed.get(index);
    }

    @Override
    public void incrementFail(String shardName) {
        int index = nameToIndex.get(shardName);
        failed.incrementAndGet(index);
        if (maxRequestTries <= failed.get(index) && isRestarterStart[index].get()) {
            startDemonRestarter(index);
        }
    }

    private void startDemonRestarter(int nodeIndex) {
        isRestarterStart[nodeIndex].set(true);
        demonRestarter.schedule(
                () -> {
                    failed.getAndSet(nodeIndex, 0);
                    isRestarterStart[nodeIndex].set(false);
                },
                RETRY_TIME_PERIOD,
                TimeUnit.MILLISECONDS
        );
    }

    @Override
    public void successRequest(String shardName) {
        failed.getAndAccumulate(nameToIndex.get(shardName), -1, SAFE_DECREMENT_FUNC);
    }

    public void close() {
        Utils.closeExecutorPool(demonRestarter);
    }

}
