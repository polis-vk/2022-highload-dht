package ok.dht.test.nadutkin.impl;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class BlockingStack<E> extends LinkedBlockingDeque<E> {

    @Override
    public boolean offer(E e) {
        return super.offerFirst(e);
    }

    @Override
    public E remove() {
        return super.removeFirst();
    }

    @Override
    public E poll() {
        return super.pollFirst();
    }

    @Override
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        return super.offerFirst(e, timeout, unit);
    }

    @Override
    public E take() throws InterruptedException {
        return super.takeFirst();
    }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        return super.pollFirst(timeout, unit);
    }

    @Override
    public void put(E e) throws InterruptedException {
        super.putFirst(e);
    }
}
