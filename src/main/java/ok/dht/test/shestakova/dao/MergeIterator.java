package ok.dht.test.shestakova.dao;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;

public final class MergeIterator<E> implements Iterator<E> {

    private final PriorityQueue<IndexedPeekIterator<E>> iterators;
    private final Comparator<E> comparator;

    private MergeIterator(PriorityQueue<IndexedPeekIterator<E>> iterators, Comparator<E> comparator) {
        this.iterators = iterators;
        this.comparator = comparator;
    }

    // iterators are strictly ordered by comparator (previous element always < next element)
    public static <E> Iterator<E> of(List<IndexedPeekIterator<E>> iterators, Comparator<E> comparator) {
        switch (iterators.size()) {
            case 0:
                return Collections.emptyIterator();
            case 1:
                return iterators.get(0);
            default:
                // Just go on
        }

        PriorityQueue<IndexedPeekIterator<E>> queue = new PriorityQueue<>(iterators.size(), (o1, o2) -> {
            int result = comparator.compare(o1.peek(), o2.peek());
            if (result != 0) {
                return result;
            }
            return Integer.compare(o1.index(), o2.index());
        });

        for (IndexedPeekIterator<E> iterator : iterators) {
            if (iterator.hasNext()) {
                queue.add(iterator);
            }
        }

        return new MergeIterator<>(queue, comparator);
    }

    @Override
    public boolean hasNext() {
        return !iterators.isEmpty();
    }

    @Override
    public E next() {
        IndexedPeekIterator<E> iterator = iterators.remove();
        E next = iterator.next();

        while (!iterators.isEmpty()) {
            IndexedPeekIterator<E> candidate = iterators.peek();
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
