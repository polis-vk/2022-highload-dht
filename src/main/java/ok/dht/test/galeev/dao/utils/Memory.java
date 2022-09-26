package ok.dht.test.galeev.dao.utils;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.galeev.dao.entry.Entry;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

import static ok.dht.test.galeev.dao.utils.FileDBWriter.getEntryLength;

public class Memory {
    private final AtomicLong byteSize;
    private final ConcurrentSkipListMap<MemorySegment, Entry<MemorySegment, MemorySegment>> delegate;

    public Memory() {
        delegate = new ConcurrentSkipListMap<>(MemorySegmentComparator.INSTANCE);
        byteSize = new AtomicLong();
    }

    public Iterator<Entry<MemorySegment, MemorySegment>> get(MemorySegment from, MemorySegment to) {
        if (from == null && to == null) {
            return delegate.values().iterator();
        } else if (from != null && to == null) {
            return delegate.tailMap(from).values().iterator();
        } else if (from == null) {
            return delegate.headMap(to).values().iterator();
        } else {
            return delegate.subMap(from, to).values().iterator();
        }
    }

    public Entry<MemorySegment, MemorySegment> get(MemorySegment key) {
        return delegate.get(key);
    }

    public long upsert(Entry<MemorySegment, MemorySegment> entry) {
        Entry<MemorySegment, MemorySegment> previousEntry = delegate.put(entry.key(), entry);
        if (previousEntry != null) {
            byteSize.addAndGet(-getEntryLength(previousEntry));
        }
        return byteSize.addAndGet(getEntryLength(entry));
    }

    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    public Iterator<Entry<MemorySegment, MemorySegment>> iterator() {
        return delegate.values().iterator();
    }

    public AtomicLong getByteSize() {
        return byteSize;
    }

    public Collection<Entry<MemorySegment, MemorySegment>> values() {
        return delegate.values();
    }
}
