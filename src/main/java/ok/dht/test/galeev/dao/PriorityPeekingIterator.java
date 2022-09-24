package ok.dht.test.galeev.dao;

import java.util.Iterator;

public class PriorityPeekingIterator<E> implements Iterator<E> {
    private final long priority;
    private final Iterator<E> delegate;
    private E current;

    public PriorityPeekingIterator(long priority, Iterator<E> delegate) {
        this.priority = priority;
        this.delegate = delegate;
    }

    public long getPriority() {
        return priority;
    }

    public E peek() {
        if (current == null) {
            current = delegate.next();
        }
        return current;
    }

    @Override
    public boolean hasNext() {
        return current != null || delegate.hasNext();
    }

    @Override
    public E next() {
        E peek = peek();
        current = null;
        return peek;
    }
}
