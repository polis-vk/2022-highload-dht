package ok.dht.test.ponomarev.dao.sstable;

import java.util.Iterator;
import java.util.NoSuchElementException;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.ponomarev.dao.TimestampEntry;

final class MappedIterator implements Iterator<TimestampEntry> {
    private final MemorySegment memorySegment;
    private long position;

    public MappedIterator(MemorySegment segment) {
        memorySegment = segment;
        position = 0;
    }

    @Override
    public boolean hasNext() {
        return memorySegment.byteSize() != position;
    }

    @Override
    public TimestampEntry next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        final long keySize = MemoryAccess.getLongAtOffset(memorySegment, position);
        position += Long.BYTES;

        final MemorySegment key = memorySegment.asSlice(position, keySize);
        position += keySize;

        final long timestamp = MemoryAccess.getLongAtOffset(memorySegment, position);
        position += Long.BYTES;

        final long valueSize = MemoryAccess.getLongAtOffset(memorySegment, position);
        position += Long.BYTES;

        if (valueSize == SSTable.TOMBSTONE_TAG) {
            return new TimestampEntry(key, null, timestamp);
        }

        MemorySegment value = memorySegment.asSlice(position, valueSize);
        position += valueSize;

        return new TimestampEntry(key, value, timestamp);
    }
}
