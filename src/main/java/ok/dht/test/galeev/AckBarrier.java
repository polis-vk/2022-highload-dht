package ok.dht.test.galeev;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AckBarrier {
    private final AtomicInteger successfulResponses;
    private final AtomicInteger unsuccessfulResponses;
    private final AtomicBoolean hasAnswered;
    private final int ack;
    private final int from;
    private volatile boolean isNeedToSendResponseToClient;

    public AckBarrier(int ack, int from) {
        this.ack = ack;
        this.from = from;
        this.successfulResponses = new AtomicInteger(0);
        this.unsuccessfulResponses = new AtomicInteger(0);
        this.hasAnswered = new AtomicBoolean(false);
    }

    public void success() {
        if (successfulResponses.incrementAndGet() >= ack) {
            isNeedToSendResponseToClient = true;
        }
    }

    public void unSuccess() {
        if (unsuccessfulResponses.incrementAndGet() >= (from - ack + 1)) {
            isNeedToSendResponseToClient = true;
        }
    }

    public boolean isAckAchieved() {
        return successfulResponses.get() >= ack;
    }

    public boolean isNeedToSendResponseToClient() {
        return isNeedToSendResponseToClient && hasAnswered.compareAndSet(false, true);
    }
}
