package ok.dht.test.nadutkin.impl.shards;

import ok.dht.test.nadutkin.impl.utils.Constants;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class CircuitBreaker {
    private final Map<String, AtomicInteger> failedAttempts;

    public CircuitBreaker(List<String> clusterUrls) {
        this.failedAttempts = clusterUrls
                .stream()
                .collect(Collectors.toMap(Function.identity(), url -> new AtomicInteger(0)));
    }

    private void process(String url, Function<Integer, Integer> next) {
        while (true) {
            Integer oldValue = failedAttempts.get(url).intValue();
            if (oldValue.equals(Constants.MAX_FAILS)) {
                return;
            }
            int newValue = next.apply(oldValue);
            if (failedAttempts.get(url).compareAndSet(oldValue, newValue)) {
                return;
            }
        }
    }

    public void success(String url) {
        process(url, oldValue -> max(0, oldValue - 1));
    }
    
    public void fail(String url) {
        process(url, oldValue -> min(Constants.MAX_FAILS, oldValue + 1));
    }

    public boolean isWorking(String url) {
        return failedAttempts.get(url).intValue() < Constants.MAX_FAILS;
    }
}
