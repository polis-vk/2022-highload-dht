package ok.dht.kovalenko.dao.iterators;

import ok.dht.kovalenko.dao.aliases.TypedEntry;
import ok.dht.kovalenko.dao.aliases.TypedIterator;

import java.util.Iterator;

public class PeekIterator implements TypedIterator {

    private final int priority;
    private final Iterator<TypedEntry> delegate;
    private TypedEntry peek;

    public PeekIterator(Iterator<TypedEntry> delegate, int priority) {
        this.delegate = delegate;
        this.priority = priority;
    }

    @Override
    public boolean hasNext() {
        return peek != null || delegate.hasNext();
    }

    @Override
    public TypedEntry next() {
        TypedEntry peekNext = peek();
        this.peek = null;
        return peekNext;
    }

    public TypedEntry peek() {
        if (peek == null && delegate.hasNext()) {
            peek = delegate.next();
        }
        return peek;
    }

    public int getPriority() {
        return priority;
    }
}
