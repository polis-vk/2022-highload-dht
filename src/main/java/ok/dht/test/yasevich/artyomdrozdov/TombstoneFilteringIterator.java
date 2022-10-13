package ok.dht.test.yasevich.artyomdrozdov;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.yasevich.dao.Entry;

import java.util.Iterator;
import java.util.NoSuchElementException;

class TombstoneFilteringIterator implements Iterator<Entry<MemorySegment>> {
    private final Iterator<Entry<MemorySegment>> iterator;
    private Entry<MemorySegment> current;

    public TombstoneFilteringIterator(Iterator<Entry<MemorySegment>> iterator) {
        this.iterator = iterator;
    }

    public Entry<MemorySegment> peek() {
        return hasNext() ? current : null;
    }

    @Override
    public boolean hasNext() {
        if (current != null) {
            return true;
        }

        while (iterator.hasNext()) {
            Entry<MemorySegment> entry = iterator.next();
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
        Entry<MemorySegment> next = current;
        current = null;
        return next;
    }
}
