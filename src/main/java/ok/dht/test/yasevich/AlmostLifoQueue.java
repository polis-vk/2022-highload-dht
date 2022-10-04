package ok.dht.test.yasevich;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AlmostLifoQueue extends LinkedBlockingDeque<Runnable> {

    private final AtomicInteger retrievedElements = new AtomicInteger();
    private final int fifoRareness;

    public AlmostLifoQueue(int capacity, int fifoRareness) {
        super(capacity);
        this.fifoRareness = fifoRareness;
    }

    @Override
    public Runnable remove() {
        if (retrievedElements.incrementAndGet() % fifoRareness != 0) {
            return super.removeLast();
        }
        return super.remove();
    }

    @Override
    public Runnable poll() {
        if (retrievedElements.incrementAndGet() % fifoRareness != 0) {
            return super.pollLast();
        }
        return super.poll();
    }

    @Override
    public Runnable poll(long timeout, TimeUnit unit) throws InterruptedException {
        if (retrievedElements.incrementAndGet() % fifoRareness != 0) {
            return super.pollLast(timeout, unit);
        }
        return super.poll(timeout, unit);
    }

    @Override
    public Runnable take() throws InterruptedException {
        if (retrievedElements.incrementAndGet() % fifoRareness != 0) {
            return takeLast();
        }
        return super.take();
    }

    @Override
    public Runnable takeFirst() throws InterruptedException {
        return super.takeFirst();
    }
}
