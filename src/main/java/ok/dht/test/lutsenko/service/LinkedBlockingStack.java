package ok.dht.test.lutsenko.service;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class LinkedBlockingStack<E> extends LinkedBlockingDeque<E> {

    public LinkedBlockingStack(int capacity) {
        super(capacity);
    }

    @Override
    public boolean offer(@Nonnull E e) {
        return super.offerLast(e);
    }

    @Override
    public E remove() {
        return super.removeLast();
    }

    @Override
    public E poll() {
        return super.pollLast();
    }

    @Override
    public E element() {
        return super.getLast();
    }

    @Override
    public E peek() {
        return super.peekLast();
    }

    @Override
    public void put(@Nonnull E e) throws InterruptedException {
        super.putLast(e);
    }

    @Override
    public boolean offer(E e, long timeout, @Nonnull TimeUnit unit) throws InterruptedException {
        return super.offerLast(e, timeout, unit);
    }

    @Override
    public E take() throws InterruptedException {
        return super.takeLast();
    }

    @Override
    public E poll(long timeout, @Nonnull TimeUnit unit) throws InterruptedException {
        return super.pollLast(timeout, unit);
    }

    @Override
    public Iterator<E> iterator() {
        return super.descendingIterator();
    }
}
