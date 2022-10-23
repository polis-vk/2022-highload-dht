package ok.dht.test.kiselyov.dao.impl;

import java.util.Iterator;

public class IndexedPeekIterator implements Iterator<EntryWithTimestamp> {

    private final int index;
    protected final Iterator<EntryWithTimestamp> delegate;
    protected EntryWithTimestamp peek;

    public IndexedPeekIterator(int index, Iterator<EntryWithTimestamp> delegate) {
        this.index = index;
        this.delegate = delegate;
    }

    public int index() {
        return index;
    }

    public EntryWithTimestamp peek() {
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
    public EntryWithTimestamp next() {
        EntryWithTimestamp result = peek();
        peek = null;
        return result;
    }
}
