package ok.dht.test.anikina.utils;

import jdk.incubator.foreign.MemorySegment;

public final class MemorySegmentUtils {
    private MemorySegmentUtils() {
    }

    public static byte[] toBytes(MemorySegment s) {
        return s == null ? null : s.toByteArray();
    }

    public static MemorySegment fromBytes(byte[] bytes) {
        return bytes == null ? null : MemorySegment.ofArray(bytes);
    }

    public static MemorySegment fromString(String data) {
        return data == null ? null : MemorySegment.ofArray(data.toCharArray());
    }
}
