package ok.dht.test.kiselyov.dao.impl;

import ok.dht.test.kiselyov.dao.BaseEntry;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class SkipNullValuesIterator implements Iterator<BaseEntry<byte[]>> {

    private final IndexedPeekIterator iterator;

    public SkipNullValuesIterator(IndexedPeekIterator iterator) {
        this.iterator = iterator;
    }

    @Override
    public boolean hasNext() {
        while (iterator.hasNext() && iterator.peek().value() == null) {
            iterator.next();
        }
        return iterator.hasNext();
    }

    @Override
    public BaseEntry<byte[]> next() {
        if (!hasNext()) {
            throw new NoSuchElementException("There is no next element!");
        }
        return iterator.next();
    }
}
