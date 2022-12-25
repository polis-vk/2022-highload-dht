package ok.dht.test.ponomarev.dao;

import jdk.incubator.foreign.MemorySegment;

public final class TimestampEntry implements Entry<MemorySegment> {
    private final Entry<MemorySegment> entry;
    private final long timestamp;

    public TimestampEntry(Entry<MemorySegment> entry) {
        this.entry = entry;
        this.timestamp = System.currentTimeMillis();
    }

    public TimestampEntry(MemorySegment key, MemorySegment value, long timestamp) {
        this.entry = new BaseEntry<>(key, value);
        this.timestamp = timestamp;
    }
    
    public long getSizeBytes() {
        final MemorySegment key = entry.key();
        final MemorySegment value = entry.value();

        return key.byteSize() + (value == null ? 0 : value.byteSize()) + Long.BYTES;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public MemorySegment key() {
        return entry.key();
    }

    @Override
    public MemorySegment value() {
        return entry.value();
    }
}
