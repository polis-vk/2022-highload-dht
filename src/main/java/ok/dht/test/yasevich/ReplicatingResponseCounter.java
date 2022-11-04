package ok.dht.test.yasevich;

import java.util.concurrent.atomic.AtomicInteger;

public class ReplicatingResponseCounter {
    private final int ack;
    private final int from;
    private final AtomicInteger successes = new AtomicInteger();
    private final AtomicInteger fails = new AtomicInteger();

    public ReplicatingResponseCounter(int ack, int from) {
        this.ack = ack;
        this.from = from;
    }

    public boolean isTimeToResponseGood() {
        return successes.incrementAndGet() == ack;
    }

    public boolean isTimeToRespondBad() {
        int currentFails = fails.incrementAndGet();
        return currentFails > (from - ack) || currentFails == from;
    }

}
