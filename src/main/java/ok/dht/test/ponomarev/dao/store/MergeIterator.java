package ok.dht.test.ponomarev.dao.store;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import ok.dht.test.ponomarev.dao.Entry;

final class MergeIterator<T, E extends Entry<T>> implements Iterator<E> {
    private final Iterator<E> oldDataIterator;
    private final Iterator<E> newDataIterator;
    private final Comparator<T> comparator;

    private E oldEntry;
    private E newEntry;

    private MergeIterator(final Iterator<E> left, final Iterator<E> right, Comparator<T> comparator) {
        this.oldDataIterator = left;
        this.newDataIterator = right;
        this.comparator = comparator;

        this.oldEntry = getElement(oldDataIterator);
        this.newEntry = getElement(newDataIterator);
    }

    public static <T, E extends Entry<T>> Iterator<E> of(List<Iterator<E>> iterators, Comparator<T> comparator) {
        if (iterators.isEmpty()) {
            return Collections.emptyIterator();
        }

        final int size = iterators.size();
        if (size == 1) {
            return iterators.get(0);
        }

        return new MergeIterator<>(
            of(iterators.subList(0, size / 2), comparator),
            of(iterators.subList(size / 2, size), comparator),
            comparator
        );
    }

    @Override
    public boolean hasNext() {
        return oldEntry != null || newEntry != null;
    }

    @Override
    public E next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No such element");
        }

        final int compareResult = compare(oldEntry, newEntry);

        if (compareResult == 0) {
            final E next = newEntry;
            oldEntry = getElement(oldDataIterator);
            newEntry = getElement(newDataIterator);

            return next;
        }

        if (compareResult < 0) {
            final E next = oldEntry;
            oldEntry = getElement(oldDataIterator);

            return next;
        }

        final E next = newEntry;
        newEntry = getElement(newDataIterator);

        return next;
    }

    private int compare(E r1, E r2) {
        if (r1 == null) {
            return 1;
        }

        if (r2 == null) {
            return -1;
        }

        return comparator.compare(r1.key(), r2.key());
    }

    private E getElement(final Iterator<E> iter) {
        return iter.hasNext() ? iter.next() : null;
    }
}
