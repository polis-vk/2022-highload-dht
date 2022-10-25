package ok.dht.test.kiselyov.dao.impl;

import ok.dht.test.kiselyov.dao.BaseEntry;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;

public final class MergeIterator implements Iterator<BaseEntry<byte[]>> {

    private final PriorityQueue<IndexedPeekIterator> iterators;
    private final Comparator<BaseEntry<byte[]>> comparator;

    private MergeIterator(PriorityQueue<IndexedPeekIterator> iterators, Comparator<BaseEntry<byte[]>> comparator) {
        this.iterators = iterators;
        this.comparator = comparator;
    }

    public static Iterator<BaseEntry<byte[]>> of(List<IndexedPeekIterator> iterators,
                                                 Comparator<BaseEntry<byte[]>> comparator) {
        switch (iterators.size()) {
            case 0:
                return Collections.emptyIterator();
            case 1:
                return iterators.get(0);
            default:
        }
        PriorityQueue<IndexedPeekIterator> queue = new PriorityQueue<>(iterators.size(), (o1, o2) -> {
            int result = comparator.compare(o1.peek(), o2.peek());
            if (result != 0) {
                return result;
            }
            return Integer.compare(o1.index(), o2.index());
        });
        for (IndexedPeekIterator iterator : iterators) {
            if (iterator.hasNext()) {
                queue.add(iterator);
            }
        }
        return new MergeIterator(queue, comparator);
    }

    @Override
    public boolean hasNext() {
        return !iterators.isEmpty();
    }

    @Override
    public BaseEntry<byte[]> next() {
        IndexedPeekIterator iterator = iterators.remove();
        BaseEntry<byte[]> next = iterator.next();
        while (!iterators.isEmpty()) {
            IndexedPeekIterator candidate = iterators.peek();
            if (comparator.compare(next, candidate.peek()) != 0) {
                break;
            }
            iterators.remove();
            candidate.next();
            if (candidate.hasNext()) {
                iterators.add(candidate);
            }
        }
        if (iterator.hasNext()) {
            iterators.add(iterator);
        }
        return next;
    }
}
