package ok.dht.test.kondraev.dao;

import java.util.Iterator;
import java.util.NoSuchElementException;

final class WithoutTombstonesIterator implements Iterator<MemorySegmentEntry> {
    final PeekIterator<MemorySegmentEntry> iterator;

    WithoutTombstonesIterator(PeekIterator<MemorySegmentEntry> iterator) {
        this.iterator = iterator;
    }

    @Override
    public boolean hasNext() {
        while (iterator.hasNext()) {
            if (!iterator.peek().isTombStone()) {
                return true;
            }
            iterator.next();
        }
        return false;
    }

    @Override
    public MemorySegmentEntry next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return iterator.next();
    }
}
