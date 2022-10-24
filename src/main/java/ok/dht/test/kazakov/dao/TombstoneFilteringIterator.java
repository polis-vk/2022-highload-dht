package ok.dht.test.kazakov.dao;

import jdk.incubator.foreign.MemorySegment;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class TombstoneFilteringIterator implements Iterator<Entry<MemorySegment>> {
    private final Iterator<Entry<MemorySegment>> iterator;
    private Entry<MemorySegment> current;

    public TombstoneFilteringIterator(final Iterator<Entry<MemorySegment>> iterator) {
        this.iterator = iterator;
    }

    @Override
    public boolean hasNext() {
        if (current != null) {
            return true;
        }

        while (iterator.hasNext()) {
            final Entry<MemorySegment> entry = iterator.next();
            if (!entry.isTombstone()) {
                this.current = entry;
                return true;
            }
        }

        return false;
    }

    @Override
    public Entry<MemorySegment> next() {
        if (!hasNext()) {
            throw new NoSuchElementException("...");
        }
        final Entry<MemorySegment> next = current;
        current = null;
        return next;
    }
}
