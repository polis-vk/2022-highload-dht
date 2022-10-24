package ok.dht.test.shestakova.dao;

import java.util.Iterator;

public class IndexedPeekIterator<E> implements Iterator<E> {

    private final int index;
    protected final Iterator<E> delegate;
    protected E peek;

    public IndexedPeekIterator(int index, Iterator<E> delegate) {
        this.index = index;
        this.delegate = delegate;
    }

    public int index() {
        return index;
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
