package ok.dht.test.ponomarev.dao;

import java.util.Comparator;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;
import one.nio.util.Utf8;

public final class Utils {
    private Utils() {}
    
    public static final Comparator<MemorySegment> COMPARATOR = (MemorySegment s1, MemorySegment s2) -> {
        final long mismatch = s1.mismatch(s2);
        if (mismatch == -1) {
            return 0;
        }

        if (mismatch == s1.byteSize()) {
            return -1;
        }

        if (mismatch == s2.byteSize()) {
            return 1;
        }

        return Byte.compare(
                MemoryAccess.getByteAtOffset(s1, mismatch),
                MemoryAccess.getByteAtOffset(s2, mismatch)
        );
    };
    
    public static int compare(MemorySegment key1, MemorySegment key2) {
        return COMPARATOR.compare(key1, key2);
    }

    public static String memorySegmentToString(MemorySegment data) {
        if (data == null) {
            return null;
        }

        return Utf8.toString(data.asByteBuffer().array());
    }

    public static MemorySegment toMemorySegment(String data) {
        if (data == null) {
            return null;
        }

        return MemorySegment.ofArray(Utf8.toBytes(data));
    }
}
