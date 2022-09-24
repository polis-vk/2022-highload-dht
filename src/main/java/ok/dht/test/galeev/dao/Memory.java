package ok.dht.test.galeev.dao;

import jdk.incubator.foreign.MemorySegment;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

import static ru.mail.polis.arturgaleev.FileDBWriter.getEntryLength;
import static ru.mail.polis.arturgaleev.MemorySegmentComparator.INSTANCE;

public class Memory {
    final AtomicLong byteSize;
    private final ConcurrentSkipListMap<MemorySegment, Entry<MemorySegment>> delegate;

    public Memory() {
        delegate = new ConcurrentSkipListMap<>(INSTANCE);
        byteSize = new AtomicLong();
    }

    public Iterator<Entry<MemorySegment>> get(MemorySegment from, MemorySegment to) {
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

    public Entry<MemorySegment> get(MemorySegment key) {
        return delegate.get(key);
    }

    public long upsert(Entry<MemorySegment> entry) {
        Entry<MemorySegment> previousEntry = delegate.put(entry.key(), entry);
        if (previousEntry != null) {
            byteSize.addAndGet(-getEntryLength(previousEntry));
        }
        return byteSize.addAndGet(getEntryLength(entry));
    }

    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    public Iterator<Entry<MemorySegment>> iterator() {
        return delegate.values().iterator();
    }

    public long getByteSize() {
        return byteSize.get();
    }

    public Collection<Entry<MemorySegment>> values() {
        return delegate.values();
    }
}
