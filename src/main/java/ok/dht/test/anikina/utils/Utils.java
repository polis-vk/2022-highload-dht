package ok.dht.test.anikina.utils;

import jdk.incubator.foreign.MemorySegment;
import one.nio.util.Utf8;

import java.nio.ByteBuffer;

public final class Utils {
    private Utils() {
    }

    public static byte[] toBytes(MemorySegment s) {
        return s == null ? null : s.toByteArray();
    }

    public static byte[] toBytes(String str) {
        return Utf8.toBytes(str);
    }

    public static MemorySegment memorySegmentFromBytes(byte[] bytes) {
        return bytes == null ? null : MemorySegment.ofArray(bytes);
    }

    public static MemorySegment memorySegmentFromString(String data) {
        return data == null ? null : memorySegmentFromBytes(toBytes(data));
    }

    public static byte[] toByteArray(byte[] timestamp, byte[] value) {
        return ByteBuffer.allocate(Long.BYTES + value.length)
                .put(timestamp)
                .put(value)
                .array();
    }

    public static byte[] toByteArray(long timestamp, byte[] value) {
        return ByteBuffer.allocate(Long.BYTES + value.length)
                .putLong(timestamp)
                .put(value)
                .array();
    }

    public static byte[] toByteArray(long timestamp) {
        return ByteBuffer.allocate(Long.BYTES)
                .putLong(timestamp)
                .array();
    }

    public static long longFromByteArray(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getLong();
    }
}
