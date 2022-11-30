package ok.dht.test.kazakov.dao;

import java.util.Iterator;

public class IterableIteratorWrapper<T> implements Iterable<T> {
    private final Iterator<T> iterator;

    public IterableIteratorWrapper(final Iterator<T> iterator) {
        this.iterator = iterator;
    }

    @Override
    public Iterator<T> iterator() {
        return iterator;
    }
}
