package ok.dht.test.yasevich;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AlmostLifoQueue extends LinkedBlockingDeque<Runnable> {

    private final AtomicInteger retrievedElements = new AtomicInteger();
    private final int fifoRareness;

    public AlmostLifoQueue(int capacity, int fifoRareness) {
        super(capacity);
        if (fifoRareness <= 0) {
            throw new IllegalArgumentException();
        }
        this.fifoRareness = fifoRareness;
    }

    @Override
    public Runnable remove() {
        if (itsTimeToFifo()) {
            return super.remove();
        }
        return super.removeLast();
    }

    @Override
    public Runnable poll() {
        if (itsTimeToFifo()) {
            return super.poll();
        }
        return super.pollLast();
    }

    @Override
    public Runnable poll(long timeout, TimeUnit unit) throws InterruptedException {
        if (itsTimeToFifo()) {
            return super.poll(timeout, unit);
        }
        return super.pollLast(timeout, unit);
    }

    @Override
    public Runnable take() throws InterruptedException {
        if (itsTimeToFifo()) {
            return super.take();
        }
        return takeLast();
    }

    private boolean itsTimeToFifo() {
        return retrievedElements.incrementAndGet() % fifoRareness == 0;
    }
}
