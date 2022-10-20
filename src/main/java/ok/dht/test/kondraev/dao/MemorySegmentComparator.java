package ok.dht.test.kondraev.dao;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;

import java.util.Comparator;

/**
 * Lexicographic comparison of UTF-8 strings can be done by byte, according to
 * <a href="https://www.rfc-editor.org/rfc/rfc3629.txt">RFC 3239</a>, page 2.
 * This string comparison likely won't work with collation different from ASCII.
 */
public final class MemorySegmentComparator implements Comparator<MemorySegment> {
    /**
     * For any {@code MemorySegment x}: {@code compare(MINIMAL, x) <= 0} is true.
     */
    public static final MemorySegment MINIMAL = MemorySegment.ofArray(new byte[]{});
    public static final MemorySegmentComparator INSTANCE = new MemorySegmentComparator();

    @Override
    public int compare(MemorySegment lhs, MemorySegment rhs) {
        long offset = lhs.mismatch(rhs);
        if (offset == -1) {
            return 0;
        }
        if (offset == lhs.byteSize()) {
            return -1;
        }
        if (offset == rhs.byteSize()) {
            return 1;
        }
        return Byte.compareUnsigned(
                MemoryAccess.getByteAtOffset(lhs, offset),
                MemoryAccess.getByteAtOffset(rhs, offset)
        );
    }

    /**
     * Use {@link MemorySegmentComparator#INSTANCE} instead.
     */
    private MemorySegmentComparator() {
    }
}
