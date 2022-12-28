package ok.dht.test.maximenko.dao;

import jdk.incubator.foreign.MemorySegment;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class MemTable implements Table {
    private static final Comparator<MemorySegment> COMPARATOR = NaturalOrderComparator.getInstance();
    private static final MemorySegment VERY_FIRST_KEY = MemorySegment.ofArray(new byte[]{});
    private final ConcurrentNavigableMap<MemorySegment, Entry<MemorySegment>> data =
            new ConcurrentSkipListMap<>(COMPARATOR);
    private final AtomicLong spaceLeft = new AtomicLong();
    private static final int SUCCESS = 1;
    private static final int FLUSH_REQUEST = -1;
    private static final int TABLE_READ_ONLY = -2;
    private final AtomicBoolean flushRequested = new AtomicBoolean(false);

    public MemTable(long tableSpace) {
        spaceLeft.set(tableSpace);
    }

    public int put(MemorySegment key, Entry<MemorySegment> entry) {
        long spaceLeftAfterInsert =
                spaceLeft.addAndGet(-(entry.value() == null ? 0 : entry.value().byteSize() + entry.key().byteSize()));

        if (spaceLeftAfterInsert >= 0) {
            Entry<MemorySegment> previousEntry;

            previousEntry = data.getOrDefault(key, null);
            data.put(key, entry);

            if (previousEntry != null) {
                spaceLeft.compareAndSet(spaceLeftAfterInsert, spaceLeftAfterInsert
                        + (previousEntry.value() == null ? 0 : previousEntry.value().byteSize())
                        + previousEntry.key().byteSize());
            }
            return SUCCESS;
        }

        if (flushRequested.compareAndSet(false, true)) {
            return FLUSH_REQUEST;
        }

        return TABLE_READ_ONLY;
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    @Override
    public Iterator<Entry<MemorySegment>> get() throws IOException {
        return get(null, null);
    }

    @Override
    public Iterator<Entry<MemorySegment>> get(MemorySegment from, MemorySegment to) throws IOException {
        MemorySegment fromValue = from;
        if (from == null) {
            fromValue = VERY_FIRST_KEY;
        }

        if (to == null) {
            return data.tailMap(fromValue).values().iterator();
        }
        return data.subMap(fromValue, to).values().iterator();

    }

    @Override
    public Iterator<Entry<MemorySegment>> iterator() {
        try {
            return get();
        } catch (IOException e) {
            return Collections.emptyIterator();
        }
    }
}
