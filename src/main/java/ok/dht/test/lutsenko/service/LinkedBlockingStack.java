package ok.dht.test.lutsenko.service;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class LinkedBlockingStack<E> implements BlockingQueue<E> {

    private final LinkedBlockingDeque<E> deque;

    public LinkedBlockingStack(int capacity) {
        this.deque = new LinkedBlockingDeque<>(capacity);
    }

    @Override
    public boolean add(@Nonnull E e) {
        return deque.add(e);
    }

    @Override
    public boolean offer(@Nonnull E e) {
        return deque.offerLast(e);
    }

    @Override
    public E remove() {
        return deque.removeLast();
    }

    @Override
    public E poll() {
        return deque.pollLast();
    }

    @Override
    public E element() {
        return deque.getLast();
    }

    @Override
    public E peek() {
        return deque.peekLast();
    }

    @Override
    public void put(@Nonnull E e) throws InterruptedException {
        deque.putLast(e);
    }

    @Override
    public boolean offer(E e, long timeout, @Nonnull TimeUnit unit) throws InterruptedException {
        return deque.offerLast(e, timeout, unit);
    }

    @Override
    public E take() throws InterruptedException {
        return deque.takeLast();
    }

    @Override
    public E poll(long timeout, @Nonnull TimeUnit unit) throws InterruptedException {
        return deque.pollLast(timeout, unit);
    }

    @Override
    public int remainingCapacity() {
        return deque.remainingCapacity();
    }

    @Override
    public boolean remove(Object o) {
        return deque.remove(o);
    }

    @Override
    public boolean containsAll(@Nonnull Collection<?> c) {
        return deque.containsAll(c);
    }

    @Override
    public boolean addAll(@Nonnull Collection<? extends E> c) {
        return deque.addAll(c);
    }

    @Override
    public boolean removeAll(@Nonnull Collection<?> c) {
        return deque.removeAll(c);
    }

    @Override
    public boolean retainAll(@Nonnull Collection<?> c) {
        return deque.retainAll(c);
    }

    @Override
    public void clear() {
        deque.clear();
    }

    @Override
    public int size() {
        return deque.size();
    }

    @Override
    public boolean isEmpty() {
        return deque.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return deque.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return deque.descendingIterator();
    }

    @Override
    public Object[] toArray() {
        return deque.toArray();
    }

    @Override
    public <T> T[] toArray(@Nonnull T[] a) {
        return deque.toArray(a);
    }

    @Override
    public int drainTo(@Nonnull Collection<? super E> c) {
        return deque.drainTo(c);
    }

    @Override
    public int drainTo(@Nonnull Collection<? super E> c, int maxElements) {
        return deque.drainTo(c, maxElements);
    }
}
