package ok.dht.test.yasevich;

import one.nio.http.HttpSession;
import one.nio.http.Response;

import java.util.concurrent.atomic.AtomicInteger;

public class ReplicatingResponseCounter {
    private final int ack;
    private final int from;
    private final AtomicInteger nonFailed = new AtomicInteger();
    private final AtomicInteger total = new AtomicInteger();

    public ReplicatingResponseCounter(int ack, int from) {
        this.ack = ack;
        this.from = from;
    }

    public boolean isTimeToResponseGood() {
        total.incrementAndGet();
        return nonFailed.incrementAndGet() == ack;
    }

    public void responseFailureIfNeeded(HttpSession session) {
        if (total.incrementAndGet() == from && nonFailed.get() < ack) {
            ServiceImpl.sendResponse(session, new Response(ReplicasManager.NOT_ENOUGH_REPLICAS, Response.EMPTY));
        }
    }
}
