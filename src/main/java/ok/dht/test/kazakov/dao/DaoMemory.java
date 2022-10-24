package ok.dht.test.kazakov.dao;

import jdk.incubator.foreign.MemorySegment;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

class DaoMemory {

    static final DaoMemory EMPTY = new DaoMemory(-1);
    private final AtomicLong size = new AtomicLong();
    private final AtomicBoolean oversized = new AtomicBoolean();

    private final ConcurrentSkipListMap<MemorySegment, Entry<MemorySegment>> delegate =
            new ConcurrentSkipListMap<>(MemorySegmentComparator.INSTANCE);

    private final long sizeThreshold;

    DaoMemory(final long sizeThreshold) {
        this.sizeThreshold = sizeThreshold;
    }

    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    public Collection<Entry<MemorySegment>> values() {
        return delegate.values();
    }

    public boolean put(final MemorySegment key, final Entry<MemorySegment> entry) {
        if (sizeThreshold == -1) {
            throw new UnsupportedOperationException("Read-only map");
        }
        final Entry<MemorySegment> segmentEntry = delegate.put(key, entry);
        long sizeDelta = StorageHelper.getSizeOnDisk(entry);
        if (segmentEntry != null) {
            sizeDelta -= StorageHelper.getSizeOnDisk(segmentEntry);
        }
        final long newSize = size.addAndGet(sizeDelta);
        if (newSize > sizeThreshold) {
            return !oversized.getAndSet(true);
        }
        return false;
    }

    public boolean overflow() {
        return !oversized.getAndSet(true);
    }

    public Iterator<Entry<MemorySegment>> get(final MemorySegment from, final MemorySegment to) {
        return to == null
                ? delegate.tailMap(from).values().iterator()
                : delegate.subMap(from, to).values().iterator();
    }

    public Entry<MemorySegment> get(final MemorySegment key) {
        return delegate.get(key);
    }
}
