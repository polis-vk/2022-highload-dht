package ok.dht.test.trofimov.dao.impl;

import ok.dht.test.trofimov.dao.Entry;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.Queue;

public class MergeIterator implements Iterator<Entry<String>> {
    private final Queue<PeekingIterator> queue = new PriorityQueue<>(PeekingIteratorComparator.INSTANCE);

    public MergeIterator(List<PeekingIterator> iterators) {
        queue.addAll(iterators);
    }

    @Override
    public boolean hasNext() {
        queue.removeIf(item -> !item.hasNext());

        // skip deleted entries
        while (!queue.isEmpty() && queue.peek().peek().value() == null) {
            updateIterators();
        }
        return !queue.isEmpty();
    }

    private Entry<String> updateIterators() {
        if (queue.size() == 1) {
            Entry<String> entry = queue.peek().next();
            if (!queue.peek().hasNext()) {
                queue.clear();
            }
            return entry;
        }
        PeekingIterator nextIter = queue.poll();
        Entry<String> nextEntry = nextIter.next();
        if (nextIter.hasNext()) {
            queue.add(nextIter);
        }
        while (!queue.isEmpty() && queue.peek().hasNext() && queue.peek().peek().key().equals(nextEntry.key())) {
            PeekingIterator peek = queue.poll();
            peek.next();
            if (peek.hasNext()) {
                queue.add(peek);
            }
        }
        return nextEntry;
    }

    @Override
    public Entry<String> next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        Entry<String> nextEntry;
        if (queue.size() > 1) {
            nextEntry = updateIterators();
        } else {
            nextEntry = queue.peek().next();
        }
        return nextEntry.value() == null ? null : nextEntry;
    }
}
