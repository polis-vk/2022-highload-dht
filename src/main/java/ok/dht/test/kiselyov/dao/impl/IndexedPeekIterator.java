package ok.dht.test.kiselyov.dao.impl;

import ok.dht.test.kiselyov.dao.BaseEntry;

import java.util.Iterator;

public class IndexedPeekIterator implements Iterator<BaseEntry<byte[]>> {

    private final int index;
    protected final Iterator<BaseEntry<byte[]>> delegate;
    protected BaseEntry<byte[]> peek;

    public IndexedPeekIterator(int index, Iterator<BaseEntry<byte[]>> delegate) {
        this.index = index;
        this.delegate = delegate;
    }

    public int index() {
        return index;
    }

    public BaseEntry<byte[]> peek() {
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
    public BaseEntry<byte[]> next() {
        BaseEntry<byte[]> result = peek();
        peek = null;
        return result;
    }
}
