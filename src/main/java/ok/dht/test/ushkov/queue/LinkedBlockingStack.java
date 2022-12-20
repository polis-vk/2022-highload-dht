package ok.dht.test.ushkov.queue;

import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public final class LinkedBlockingStack<T> extends LinkedBlockingDeque<T> {
    private LinkedBlockingStack(int capacity) {
        super(capacity);
    }

    // Static generator used to hide public methods of LinkedBlockingDeque.
    public static <T> BlockingQueue<T> newInstance(int capacity) {
        return new LinkedBlockingStack<>(capacity);
    }

    @Override
    public boolean add(T t) {
        return super.offerFirst(t);
    }

    @Override
    public boolean offer(T t) {
        return super.offerFirst(t);
    }

    @Override
    public T remove() {
        return super.removeFirst();
    }

    @Override
    public T poll() {
        return super.pollFirst();
    }

    @Override
    public T element() {
        return super.getFirst();
    }

    @Override
    public T peek() {
        return super.peekFirst();
    }

    @Override
    public void put(T t) throws InterruptedException {
        super.putFirst(t);
    }

    @Override
    public boolean offer(T t, long timeout, TimeUnit unit) throws InterruptedException {
        return super.offerFirst(t, timeout, unit);
    }

    @Override
    public T take() throws InterruptedException {
        return super.takeFirst();
    }

    @Override
    public T poll(long timeout, TimeUnit unit) throws InterruptedException {
        return super.pollFirst(timeout, unit);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        for (T e : c) {
            if (!add(e)) {
                return false;
            }
        }
        return true;
    }
}
