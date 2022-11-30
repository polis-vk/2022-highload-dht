package ok.dht.test.shestakova.dao;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;

import java.util.Comparator;

public final class MemorySegmentComparator implements Comparator<MemorySegment> {

    public static final Comparator<MemorySegment> INSTANCE = new MemorySegmentComparator();

    private MemorySegmentComparator() {
    }

    @Override
    public int compare(MemorySegment o1, MemorySegment o2) {
        long offset = o1.mismatch(o2);

        if (offset == -1) {
            return 0;
        }
        if (offset == o1.byteSize()) {
            return -1;
        }
        if (offset == o2.byteSize()) {
            return 1;
        }

        return Byte.compareUnsigned(
                MemoryAccess.getByteAtOffset(o1, offset),
                MemoryAccess.getByteAtOffset(o2, offset)
        );
    }
}
