package ok.dht.test.kosnitskiy.dao;

import jdk.incubator.foreign.MemorySegment;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

class Memory {

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
        long sizeDelta = StorageUtils.getSizeOnDisk(entry);
        if (segmentEntry != null) {
            sizeDelta -= StorageUtils.getSizeOnDisk(segmentEntry);
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
