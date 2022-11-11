package ok.dht.test.kondraev.dao;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class PeekIterator<E> implements Iterator<E> {
    private final Iterator<E> delegate;
    private E peekedElement;

    public PeekIterator(Iterator<E> delegate) {
        this.delegate = delegate;
    }

    /**
     * Peek next element.
     * @return element, returned by subsequent call of {@link Iterator#next()}, but without removing it.
     * @throws NoSuchElementException if the iteration has no more elements
     */
    public E peek() throws NoSuchElementException {
        if (peekedElement == null) {
            peekedElement = delegate.next();
        }
        return peekedElement;
    }

    @Override
    public boolean hasNext() {
        return peekedElement != null || delegate.hasNext();
    }

    @Override
    public E next() {
        try {
            return peek();
        } finally {
            peekedElement = null;
        }
    }
}
