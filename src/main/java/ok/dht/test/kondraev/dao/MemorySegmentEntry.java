package ok.dht.test.kondraev.dao;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;

public final class MemorySegmentEntry {
    public final MemorySegment key;
    public final MemorySegment value;
    public final long byteSize;

    private MemorySegmentEntry(MemorySegment key, MemorySegment value, long byteSize) {
        this.key = key;
        this.value = value;
        this.byteSize = byteSize;
    }

    public static MemorySegmentEntry of(MemorySegment key, MemorySegment value) {
        return new MemorySegmentEntry(
                key,
                value,
                Long.BYTES + (value == null ? 0 : value.byteSize()) + key.byteSize());
    }

    public static MemorySegmentEntry of(MemorySegment entrySegment) {
        long valueSize = MemoryAccess.getIntAtOffset(entrySegment, 0L);
        return new MemorySegmentEntry(
                entrySegment.asSlice(Long.BYTES + Math.max(valueSize, 0)),
                valueSize < 0 ? null : entrySegment.asSlice(Long.BYTES, valueSize),
                entrySegment.byteSize());
    }

    public void copyTo(MemorySegment entrySegment) {
        MemoryAccess.setLongAtOffset(entrySegment, 0L, isTombStone() ? -1 : value.byteSize());
        if (!isTombStone()) {
            entrySegment.asSlice(Long.BYTES, value.byteSize()).copyFrom(value);
        }
        entrySegment.asSlice(Long.BYTES + (isTombStone() ? 0 : value.byteSize())).copyFrom(key);
    }

    /**
     * Determines if this is deletion entry.
     */
    public boolean isTombStone() {
        return value == null;
    }
}
