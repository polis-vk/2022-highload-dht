package ok.dht.test.ushkov.queue;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class LinkedBlockingStack<T> implements BlockingQueue<T> {
    private final LinkedBlockingDeque<T> deque;

    public LinkedBlockingStack(int capacity) {
        deque = new LinkedBlockingDeque<>(capacity);
    }

    @Override
    public boolean add(T t) {
        return deque.offerFirst(t);
    }

    @Override
    public boolean offer(T t) {
        return deque.offerFirst(t);
    }

    @Override
    public T remove() {
        return deque.removeFirst();
    }

    @Override
    public T poll() {
        return deque.pollFirst();
    }

    @Override
    public T element() {
        return deque.getFirst();
    }

    @Override
    public T peek() {
        return deque.peekFirst();
    }

    @Override
    public void put(T t) throws InterruptedException {
        deque.putFirst(t);
    }

    @Override
    public boolean offer(T t, long timeout, TimeUnit unit) throws InterruptedException {
        return deque.offerFirst(t, timeout, unit);
    }

    @Override
    public T take() throws InterruptedException {
        return deque.takeFirst();
    }

    @Override
    public T poll(long timeout, TimeUnit unit) throws InterruptedException {
        return deque.pollFirst(timeout, unit);
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
    public boolean containsAll(Collection<?> c) {
        return deque.containsAll(c);
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

    @Override
    public boolean removeAll(Collection<?> c) {
        return deque.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
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
    public Iterator<T> iterator() {
        return deque.iterator();
    }

    @Override
    public Object[] toArray() {
        return deque.toArray();
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        return deque.toArray(a);
    }

    @Override
    public int drainTo(Collection<? super T> c) {
        return deque.drainTo(c);
    }

    @Override
    public int drainTo(Collection<? super T> c, int maxElements) {
        return deque.drainTo(c, maxElements);
    }
}
