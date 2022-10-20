package ok.dht.test.kondraev.dao;

import jdk.incubator.foreign.MemorySegment;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

class MemoryTable {
    private final AtomicLong byteSize = new AtomicLong();
    private final ConcurrentNavigableMap<MemorySegment, MemorySegmentEntry> map =
            new ConcurrentSkipListMap<>(MemorySegmentComparator.INSTANCE);

    /**
     * Insert or update entry.
     * @param entry inserted entry
     * @return byteSize after upsert
     */
    public long upsert(MemorySegmentEntry entry) {
        // implicit check for non-null entry and entry.key()
        MemorySegmentEntry was = map.put(entry.key, entry);
        return byteSize.addAndGet(SortedStringTable.sizeDelta(was, entry.byteSize));
    }

    public MemorySegmentEntry get(MemorySegment key) {
        return map.get(key);
    }

    public Iterator<MemorySegmentEntry> get(MemorySegment from, MemorySegment to) {
        Map<MemorySegment, MemorySegmentEntry> subMap = to == null ? map.tailMap(from) : map.subMap(from, to);
        return iterator(subMap);
    }

    public Collection<MemorySegmentEntry> values() {
        return map.values();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    private static <K, V> Iterator<V> iterator(Map<K, V> map) {
        return map.values().iterator();
    }
}
