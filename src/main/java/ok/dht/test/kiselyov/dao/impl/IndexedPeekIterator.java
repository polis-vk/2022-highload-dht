package ok.dht.test.kiselyov.dao.impl;

import ok.dht.test.kiselyov.dao.BaseEntry;

import java.util.Iterator;

public class IndexedPeekIterator implements Iterator<BaseEntry<byte[], Long>> {

    private final int index;
    protected final Iterator<BaseEntry<byte[], Long>> delegate;
    protected BaseEntry<byte[], Long> peek;

    public IndexedPeekIterator(int index, Iterator<BaseEntry<byte[], Long>> delegate) {
        this.index = index;
        this.delegate = delegate;
    }

    public int index() {
        return index;
    }

    public BaseEntry<byte[], Long> peek() {
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
    public BaseEntry<byte[], Long> next() {
        BaseEntry<byte[], Long> result = peek();
        peek = null;
        return result;
    }
}
