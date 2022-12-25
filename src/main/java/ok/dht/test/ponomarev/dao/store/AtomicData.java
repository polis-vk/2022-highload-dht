package ok.dht.test.ponomarev.dao.store;

import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.ponomarev.dao.TimestampEntry;
import ok.dht.test.ponomarev.dao.Utils;

public class AtomicData {
    public final SortedMap<MemorySegment, TimestampEntry> memTable;
    public final SortedMap<MemorySegment, TimestampEntry> flushData;

    public AtomicData(
        SortedMap<MemorySegment, TimestampEntry> memTable,
        SortedMap<MemorySegment, TimestampEntry> flushData
    ) {
        this.memTable = memTable;
        this.flushData = flushData;
    }

    public static AtomicData beforeFlush(AtomicData data) {
        return new AtomicData(
            new ConcurrentSkipListMap<>(Utils.COMPARATOR),
            data.memTable
        );
    }

    public static AtomicData afterFlush(AtomicData data) {
        return new AtomicData(data.memTable, new ConcurrentSkipListMap<>(Utils.COMPARATOR));
    }
}
