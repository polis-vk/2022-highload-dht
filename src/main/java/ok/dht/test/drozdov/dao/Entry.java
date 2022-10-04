package ok.dht.test.drozdov.dao;

import jdk.incubator.foreign.MemorySegment;

public class Entry {
    private final MemorySegment key;
    private final MemorySegment value;

    public Entry(MemorySegment key, MemorySegment value) {
        this.key = key;
        this.value = value;
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
}
