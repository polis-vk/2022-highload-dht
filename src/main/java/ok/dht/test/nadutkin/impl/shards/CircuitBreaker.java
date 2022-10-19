package ok.dht.test.nadutkin.impl.shards;

import ok.dht.test.nadutkin.impl.utils.Constants;

import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.Function;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class CircuitBreaker {
    private final AtomicIntegerArray failedAttempts;

    public CircuitBreaker(int size) {
        this.failedAttempts = new AtomicIntegerArray(size);
    }

    private void process(int index, Function<Integer, Integer> next) {
        while (true) {
            int oldValue = failedAttempts.get(index);
            if (oldValue == Constants.MAX_FAILS) {
                return;
            }
            int newValue = next.apply(oldValue);
            if (failedAttempts.compareAndSet(index, oldValue, newValue)) {
                return;
            }
        }
    }

    public void success(int index) {
        process(index, oldValue -> max(0, oldValue - 1));
    }
    
    public void fail(int index) {
        process(index, oldValue -> min(Constants.MAX_FAILS, oldValue + 1));
    }

    public boolean isWorking(int index) {
        return failedAttempts.get(index) < Constants.MAX_FAILS;
    }
}
