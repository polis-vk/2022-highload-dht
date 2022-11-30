package ok.dht.test.ushkov.queue;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class MSQueue<T> extends AbstractQueue<T> implements BlockingQueue<T> {
    private final AtomicReference<Node<T>> head;
    private final AtomicReference<Node<T>> tail;

    public MSQueue() {
        Node<T> dummy = new Node<T>(null);
        this.head = new AtomicReference<>(dummy);
        this.tail = new AtomicReference<>(dummy);
    }

    // Not thread safe method
    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            private Node<T> current = head.get().next.get();

            @Override
            public boolean hasNext() {
                return current.next != null;
            }

            @Override
            public T next() {
                T value = current.value;
                current = current.next.get();
                return value;
            }
        };
    }

    // Not thread safe method
    @Override
    public int size() {
        int count = 0;
        for (Iterator<T> it = iterator(); it.hasNext(); it.next()) {
            count++;
        }
        return count;
    }

    @Override
    public boolean offer(T value) {
        Node<T> newTail = new Node(value);
        while (true) {
            Node<T> currentTail = tail.get();
            if (currentTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(currentTail, newTail);
                return true;
            } else {
                tail.compareAndSet(currentTail, currentTail.next.get());
            }
        }
    }

    @Override
    public T poll() {
        while (true) {
            Node<T> currentHead = head.get();
            Node<T> currentTail = tail.get();
            Node<T> next = currentHead.next.get();
            if (currentHead == head.get()) {
                if (currentHead.equals(currentTail)) {
                    if (next == null) {
                        return null;
                    }
                    tail.compareAndSet(currentTail, next);
                } else {
                    if (head.compareAndSet(currentHead, next)) {
                        return next.value;
                    }
                }
            }
        }
    }

    public T peek() {
        Node<T> currentHead = head.get();
        Node<T> currentHeadNext = currentHead.next.get();
        return currentHeadNext == null ? null : currentHeadNext.value;
    }

    @Override
    public void put(T t) throws InterruptedException {
        offer(t);
    }

    @Override
    public boolean offer(T t, long timeout, TimeUnit unit) throws InterruptedException {
        return offer(t);
    }

    @Override
    public T take() throws InterruptedException {
        return poll();
    }

    @Override
    public T poll(long timeout, TimeUnit unit) throws InterruptedException {
        return poll();
    }

    @Override
    public int remainingCapacity() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int drainTo(Collection<? super T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int drainTo(Collection<? super T> c, int maxElements) {
        throw new UnsupportedOperationException();
    }

    private static class Node<T> {
        final T value;
        AtomicReference<Node<T>> next = new AtomicReference<>(null);

        Node(T value) {
            this.value = value;
        }
    }
}

