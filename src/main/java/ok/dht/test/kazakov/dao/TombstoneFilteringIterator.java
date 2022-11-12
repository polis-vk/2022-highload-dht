package ok.dht.test.kazakov.dao;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class TombstoneFilteringIterator<D> implements Iterator<Entry<D>> {
    private final Iterator<Entry<D>> iterator;
    private Entry<D> current;

    public TombstoneFilteringIterator(final Iterator<Entry<D>> iterator) {
        this.iterator = iterator;
    }

    public Entry<D> peek() {
        return hasNext() ? current : null;
    }

    @Override
    public boolean hasNext() {
        if (current != null) {
            return true;
        }

        while (iterator.hasNext()) {
            final Entry<D> entry = iterator.next();
            if (!entry.isTombstone()) {
                this.current = entry;
                return true;
            }
        }

        return false;
    }

    @Override
    public Entry<D> next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No element found for TombstoneFilteringIterator");
        }
        final Entry<D> next = current;
        current = null;
        return next;
    }
}
