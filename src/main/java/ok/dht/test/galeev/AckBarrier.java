package ok.dht.test.galeev;

import one.nio.http.HttpSession;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class AckBarrier {
    private static final Logger LOGGER = LoggerFactory.getLogger(AckBarrier.class);
    private final AtomicInteger successfulResponses;
    private final AtomicInteger unsuccessfulResponses;
    private final CountDownLatch continueBarrier;
    private final int ack;
    private final int from;

    public AckBarrier(int ack, int from) {
        this.ack = ack;
        this.from = from;
        this.continueBarrier = new CountDownLatch(1);
        this.successfulResponses = new AtomicInteger(0);
        this.unsuccessfulResponses = new AtomicInteger(0);
    }

    public boolean waitContinueBarrier(HttpSession session, String msg) throws IOException {
        try {
            return continueBarrier.await(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            session.sendResponse(new Response(Response.INTERNAL_ERROR, Response.EMPTY));
            LOGGER.error(msg, e);
            Thread.currentThread().interrupt();
        }
        return false;
    }

    public void success() {
        if (successfulResponses.incrementAndGet() >= ack) {
            continueBarrier.countDown();
        }
    }

    public void unSuccess() {
        if (unsuccessfulResponses.incrementAndGet() >= (from - ack + 1)) {
            continueBarrier.countDown();
        }
    }

    public boolean isAckAchieved() {
        return successfulResponses.get() >= ack;
    }

    public Consumer<Boolean> getDefaultSuccessChecker() {
        return (isSuccessful) -> {
            if (isSuccessful) {
                success();
            } else {
                unSuccess();
            }
        };
    }

    public Consumer<Boolean> getSuccessChecker() {
        return (isSuccessful) -> {
            if (isSuccessful) {
                success();
            } else {
                unSuccess();
            }
        };
    }
}
