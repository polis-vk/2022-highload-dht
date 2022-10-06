package ok.dht.test.drozdov.dao;

import java.util.Iterator;

public class PeekIterator<E> implements Iterator<E> {

    private final Iterator<E> delegate;
    private E peek;

    public PeekIterator(Iterator<E> delegate) {
        this.delegate = delegate;
    }

    public E peek() {
        if (peek == null && delegate.hasNext()) {
            peek = delegate.next();
        }
        return peek;
    }

    @Override
    public boolean hasNext() {
        return peek != null || delegate.hasNext();
    }

    @Override
    public E next() {
        E result = peek();
        peek = null;
        return result;
    }
}
