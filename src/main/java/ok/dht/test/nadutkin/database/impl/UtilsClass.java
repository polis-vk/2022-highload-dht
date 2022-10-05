package ok.dht.test.nadutkin.database.impl;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.nadutkin.database.Config;
import ok.dht.test.nadutkin.database.Entry;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static ok.dht.test.nadutkin.database.impl.StorageMethods.getSizeOnDisk;

public final class UtilsClass {
    private UtilsClass() {
    }

    public interface Data extends Iterable<Entry<MemorySegment>> {
        @Override
        Iterator<Entry<MemorySegment>> iterator();
    }

    public static class TombstoneFilteringIterator implements Iterator<Entry<MemorySegment>> {
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

    public static class State {
        final Config config;
        final Memory memory;
        final Memory flushing;
        final Storage storage;
        final boolean closed;

        State(Config config, Memory memory, Memory flushing, Storage storage) {
            this.config = config;
            this.memory = memory;
            this.flushing = flushing;
            this.storage = storage;
            this.closed = false;
        }

        State(Config config, Storage storage, boolean closed) {
            this.config = config;
            this.memory = Memory.EMPTY;
            this.flushing = Memory.EMPTY;
            this.storage = storage;
            this.closed = closed;
        }

        static State newState(Config config, Storage storage) {
            return new State(
                    config,
                    new Memory(config.flushThresholdBytes()),
                    Memory.EMPTY,
                    storage
            );
        }

        public State prepareForFlush() {
            checkNotClosed();
            if (isFlushing()) {
                throw new IllegalStateException("Already flushing");
            }
            return new State(
                    config,
                    new Memory(config.flushThresholdBytes()),
                    memory,
                    storage
            );
        }

        public State afterFlush(Storage storage) {
            checkNotClosed();
            if (!isFlushing()) {
                throw new IllegalStateException("Wasn't flushing");
            }
            return new State(
                    config,
                    memory,
                    Memory.EMPTY,
                    storage
            );
        }

        public State afterCompact(Storage storage) {
            checkNotClosed();
            return new State(
                    config,
                    memory,
                    flushing,
                    storage
            );
        }

        public State afterClosed() {
            checkNotClosed();
            if (!storage.isClosed()) {
                throw new IllegalStateException("Storage should be closed early");
            }
            return new State(config, storage, true);
        }

        public void checkNotClosed() {
            if (closed) {
                throw new IllegalStateException("Already closed");
            }
        }

        public boolean isFlushing() {
            return this.flushing != Memory.EMPTY;
        }
    }

    public static class Memory {

        static final Memory EMPTY = new Memory(-1);
        private final AtomicLong size = new AtomicLong();
        private final AtomicBoolean oversized = new AtomicBoolean();

        private final ConcurrentSkipListMap<MemorySegment, Entry<MemorySegment>> delegate =
                new ConcurrentSkipListMap<>(MemorySegmentComparator.INSTANCE);

        private final long sizeThreshold;

        Memory(long sizeThreshold) {
            this.sizeThreshold = sizeThreshold;
        }

        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        public Collection<Entry<MemorySegment>> values() {
            return delegate.values();
        }

        public boolean put(MemorySegment key, Entry<MemorySegment> entry) {
            if (sizeThreshold == -1) {
                throw new UnsupportedOperationException("Read-only map");
            }
            Entry<MemorySegment> segmentEntry = delegate.put(key, entry);
            long sizeDelta = getSizeOnDisk(entry);
            if (segmentEntry != null) {
                sizeDelta -= getSizeOnDisk(segmentEntry);
            }
            long newSize = size.addAndGet(sizeDelta);
            if (newSize > sizeThreshold) {
                return !oversized.getAndSet(true);
            }
            return false;
        }

        public boolean overflow() {
            return !oversized.getAndSet(true);
        }

        public Iterator<Entry<MemorySegment>> get(MemorySegment from, MemorySegment to) {
            return to == null
                    ? delegate.tailMap(from).values().iterator()
                    : delegate.subMap(from, to).values().iterator();
        }

        public Entry<MemorySegment> get(MemorySegment key) {
            return delegate.get(key);
        }
    }
}
