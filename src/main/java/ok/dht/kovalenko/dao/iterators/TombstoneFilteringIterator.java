package ok.dht.kovalenko.dao.iterators;

import ok.dht.kovalenko.dao.aliases.TypedEntry;
import ok.dht.kovalenko.dao.aliases.TypedIterator;
import ok.dht.kovalenko.dao.utils.MergeIteratorUtils;

import java.util.NoSuchElementException;
import java.util.Queue;

public class TombstoneFilteringIterator
        implements TypedIterator {

    private final Queue<PeekIterator> iterators;

    public TombstoneFilteringIterator(Queue<PeekIterator> iterators) {
        this.iterators = iterators;
    }

    @Override
    public boolean hasNext() {
        iterators.removeIf(this::hasNotNext);
        skipTombstones();
        return !iterators.isEmpty();
    }

    @Override
    public TypedEntry next() {
        if (!hasNext()) {
            throw new NoSuchElementException("There is no next iterable element");
        }
        return iterators.peek().next(); // non null! (check is below)
    }

    private boolean hasNotNext(PeekIterator iterator) {
        return !iterator.hasNext();
    }

    private void skipTombstones() {
        while (!iterators.isEmpty() && hasTombstoneForFirstElement()) {
            if (iterators.size() == 1) {
                skipTombstonesInLastOneStandingIterator();
                return;
            }

            PeekIterator first = iterators.peek();
            while (first != null && first.hasNext() && first.peek().isTombstone()) {
                MergeIteratorUtils.skipEntry(iterators, first.peek());
                first = iterators.peek();
            }
        }
    }

    private boolean hasTombstoneForFirstElement() {
        PeekIterator first = iterators.remove();
        iterators.add(first);
        return !iterators.isEmpty() && iterators.peek().peek().isTombstone();
    }

    private void skipTombstonesInLastOneStandingIterator() {
        if (iterators.isEmpty()) {
            return;
        }

        PeekIterator first = iterators.peek();
        while (first.hasNext() && first.peek().isTombstone()) {
            first.next();
        }

        if (!first.hasNext()) {
            iterators.remove();
        }
    }
}
