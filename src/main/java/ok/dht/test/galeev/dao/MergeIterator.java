package ok.dht.test.galeev.dao;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;

public class MergeIterator<E> implements Iterator<Entry<E>> {
    private final PriorityQueue<PriorityPeekingIterator<Entry<E>>> iteratorsQueue;
    private final Comparator<E> keyComparator;
    private Entry<E> currentEntry;
    private Comparator<PriorityPeekingIterator<Entry<E>>> iteratorComparator;

    // Low priority = old value
    // High priority = new value
    public MergeIterator(
            PriorityPeekingIterator<Entry<E>> iterator1,
            PriorityPeekingIterator<Entry<E>> iterator2,
            Comparator<E> keyComparator
    ) {
        this.keyComparator = keyComparator;
        iteratorsQueue = new PriorityQueue<>(2, getIteratorComparator());

        if (iterator2.hasNext()) {
            iteratorsQueue.add(iterator2);
        }
        if (iterator1.hasNext()) {
            iteratorsQueue.add(iterator1);
        }
    }

    public MergeIterator(List<PriorityPeekingIterator<Entry<E>>> iterators, Comparator<E> keyComparator) {
        this.keyComparator = keyComparator;
        int iterSize = iterators.isEmpty() ? 1 : iterators.size();
        iteratorsQueue = new PriorityQueue<>(iterSize, getIteratorComparator());

        for (PriorityPeekingIterator<Entry<E>> inFilesIterator : iterators) {
            if (inFilesIterator.hasNext()) {
                iteratorsQueue.add(inFilesIterator);
            }
        }
    }

    private Comparator<PriorityPeekingIterator<Entry<E>>> getIteratorComparator() {
        if (iteratorComparator == null) {
            iteratorComparator = (PriorityPeekingIterator<Entry<E>> it1,
                                  PriorityPeekingIterator<Entry<E>> it2
            ) -> {
                if (keyComparator.compare(it1.peek().key(), it2.peek().key()) < 0) {
                    return -1;
                } else if (keyComparator.compare(it1.peek().key(), it2.peek().key()) == 0) {
                    // reverse compare
                    return Long.compare(it2.getPriority(), it1.getPriority());
                } else {
                    return 1;
                }
            };
        }
        return iteratorComparator;
    }

    @Override
    public boolean hasNext() {
        if (currentEntry == null) {
            currentEntry = nullableNext();
            return currentEntry != null;
        }
        return true;
    }

    @Override
    public Entry<E> next() {
        Entry<E> entry = nullableNext();
        if (entry == null) {
            throw new NoSuchElementException();
        } else {
            return entry;
        }
    }

    public Entry<E> nullableNext() {
        if (currentEntry != null) {
            Entry<E> prev = currentEntry;
            currentEntry = null;
            return prev;
        }

        return getNotDeletedEntry();
    }

    private Entry<E> getNotDeletedEntry() {
        Entry<E> entry;

        while (!iteratorsQueue.isEmpty()) {
            entry = getNextEntry();
            removeElementsWithKey(entry.key());

            if (entry.value() != null) {
                return entry;
            }
        }
        return null;
    }

    private Entry<E> getNextEntry() {
        Entry<E> entry;
        PriorityPeekingIterator<Entry<E>> iterator;
        if (iteratorsQueue.size() == 1) {
            iterator = iteratorsQueue.peek();
            entry = iterator.next();
            if (!iterator.hasNext()) {
                // clear faster than poll
                iteratorsQueue.clear();
            }
        } else {
            iterator = iteratorsQueue.poll();
            entry = iterator.next();
            if (iterator.hasNext()) {
                iteratorsQueue.add(iterator);
            }
        }
        return entry;
    }

    private void removeElementsWithKey(E lastKey) {
        while (!iteratorsQueue.isEmpty() && keyComparator.compare(lastKey, iteratorsQueue.peek().peek().key()) == 0) {
            PriorityPeekingIterator<Entry<E>> iterator;
            if (iteratorsQueue.size() == 1) {
                iterator = iteratorsQueue.peek();
                iterator.next();
                if (!iterator.hasNext()) {
                    iteratorsQueue.poll();
                }
            } else {
                iterator = iteratorsQueue.poll();
                if (iterator.hasNext()) {
                    iterator.next();
                    if (iterator.hasNext()) {
                        iteratorsQueue.add(iterator);
                    }
                }
            }
        }
    }

    public Entry<E> peek() {
        if (nullablePeek() == null) {
            throw new NoSuchElementException();
        }
        return currentEntry;
    }

    public Entry<E> nullablePeek() {
        if (currentEntry == null) {
            currentEntry = nullableNext();
        }
        return currentEntry;
    }
}
