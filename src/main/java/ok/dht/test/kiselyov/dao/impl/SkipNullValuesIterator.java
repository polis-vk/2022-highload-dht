package ok.dht.test.kiselyov.dao.impl;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class SkipNullValuesIterator implements Iterator<EntryWithTimestamp> {

    private final IndexedPeekIterator iterator;

    public SkipNullValuesIterator(IndexedPeekIterator iterator) {
        this.iterator = iterator;
    }

    @Override
    public boolean hasNext() {
        while (iterator.hasNext() && iterator.peek().getEntry().value() == null) {
            iterator.next();
        }
        return iterator.hasNext();
    }

    @Override
    public EntryWithTimestamp next() {
        if (!hasNext()) {
            throw new NoSuchElementException("There is no next element!");
        }
        return iterator.next();
    }
}
