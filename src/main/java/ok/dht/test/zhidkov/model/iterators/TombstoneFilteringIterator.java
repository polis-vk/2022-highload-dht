package ok.dht.test.zhidkov.model.iterators;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.zhidkov.model.storage.Entry;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class TombstoneFilteringIterator implements Iterator<Entry<MemorySegment>> {
        private final Iterator<Entry<MemorySegment>> iterator;
        private Entry<MemorySegment> current;

        public TombstoneFilteringIterator(Iterator<Entry<MemorySegment>> iterator) {
            this.iterator = iterator;
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
