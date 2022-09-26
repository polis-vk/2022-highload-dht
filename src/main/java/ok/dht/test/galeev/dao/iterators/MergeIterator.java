package ok.dht.test.galeev.dao.iterators;

import ok.dht.test.galeev.dao.entry.Entry;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;

public class MergeIterator<E, V> implements Iterator<Entry<E, V>> {
    private final PriorityQueue<PriorityPeekingIterator<Entry<E, V>>> iteratorsQueue;
    private final Comparator<E> keyComparator;
    private Entry<E, V> currentEntry;
    private Comparator<PriorityPeekingIterator<Entry<E, V>>> iteratorComparator;

    // Low priority = old value
    // High priority = new value
    public MergeIterator(
            PriorityPeekingIterator<Entry<E, V>> iterator1,
            PriorityPeekingIterator<Entry<E, V>> iterator2,
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

    public MergeIterator(List<PriorityPeekingIterator<Entry<E, V>>> iterators, Comparator<E> keyComparator) {
        this.keyComparator = keyComparator;
        int iterSize = iterators.isEmpty() ? 1 : iterators.size();
        iteratorsQueue = new PriorityQueue<>(iterSize, getIteratorComparator());

        for (PriorityPeekingIterator<Entry<E, V>> inFilesIterator : iterators) {
            if (inFilesIterator.hasNext()) {
                iteratorsQueue.add(inFilesIterator);
            }
        }
    }

    private Comparator<PriorityPeekingIterator<Entry<E, V>>> getIteratorComparator() {
        if (iteratorComparator == null) {
            iteratorComparator = (PriorityPeekingIterator<Entry<E, V>> it1,
                                  PriorityPeekingIterator<Entry<E, V>> it2
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
    public Entry<E, V> next() {
        Entry<E, V> entry = nullableNext();
        if (entry == null) {
            throw new NoSuchElementException();
        } else {
            return entry;
        }
    }

    public Entry<E, V> nullableNext() {
        if (currentEntry != null) {
            Entry<E, V> prev = currentEntry;
            currentEntry = null;
            return prev;
        }

        return getNotDeletedEntry();
    }

    private Entry<E, V> getNotDeletedEntry() {
        Entry<E, V> entry;

        while (!iteratorsQueue.isEmpty()) {
            entry = getNextEntry();
            removeElementsWithKey(entry.key());

            if (entry.value() != null) {
                return entry;
            }
        }
        return null;
    }

    private Entry<E, V> getNextEntry() {
        Entry<E, V> entry;
        PriorityPeekingIterator<Entry<E, V>> iterator;
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
            PriorityPeekingIterator<Entry<E, V>> iterator;
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

    public Entry<E, V> peek() {
        if (nullablePeek() == null) {
            throw new NoSuchElementException();
        }
        return currentEntry;
    }

    public Entry<E, V> nullablePeek() {
        if (currentEntry == null) {
            currentEntry = nullableNext();
        }
        return currentEntry;
    }

}
