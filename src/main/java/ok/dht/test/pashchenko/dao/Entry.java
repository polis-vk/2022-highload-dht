package ok.dht.test.pashchenko.dao;

import jdk.incubator.foreign.MemorySegment;

public class Entry {
    private final MemorySegment key;
    private final MemorySegment value;
    private final long timestamp;

    public Entry(MemorySegment key, MemorySegment value, long timestamp) {
        this.key = key;
        this.value = value;
        this.timestamp = timestamp;
    }

    public MemorySegment key() {
        return key;
    }

    public MemorySegment value() {
        return value;
    }

    public boolean isTombstone() {
        return value == null;
    }

    public long timestamp() {
        return timestamp;
    }
}
