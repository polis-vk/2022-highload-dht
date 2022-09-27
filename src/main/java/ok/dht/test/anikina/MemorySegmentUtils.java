package ok.dht.test.anikina;

import jdk.incubator.foreign.MemorySegment;

final class MemorySegmentUtils {
    static byte[] toBytes(MemorySegment s) {
        return s == null ? null : s.toByteArray();
    }

    static MemorySegment fromBytes(byte[] bytes) {
        return bytes == null ? null : MemorySegment.ofArray(bytes);
    }

    static MemorySegment fromString(String data) {
        return data == null ? null : MemorySegment.ofArray(data.toCharArray());
    }
}
