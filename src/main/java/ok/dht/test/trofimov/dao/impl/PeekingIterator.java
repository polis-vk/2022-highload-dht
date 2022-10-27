package ok.dht.test.trofimov.dao.impl;

import ok.dht.test.trofimov.dao.Entry;

import java.util.Iterator;

public class PeekingIterator implements Iterator<Entry<String>> {

    private final Iterator<Entry<String>> iterator;
    private Entry<String> currentEntry;
    private final int priority;

    public PeekingIterator(Iterator<Entry<String>> iterator, int priority) {
        this.iterator = iterator;
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }

    public Entry<String> peek() {
        if (currentEntry == null) {
            currentEntry = iterator.next();
        }
        return currentEntry;
    }

    @Override
    public boolean hasNext() {
        return currentEntry != null || iterator.hasNext();
    }

    @Override
    public Entry<String> next() {
        Entry<String> peek = peek();
        currentEntry = null;
        return peek;
    }
}
