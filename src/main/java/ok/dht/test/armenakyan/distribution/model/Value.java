package ok.dht.test.armenakyan.distribution.model;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class Value {
    private final long timestamp;
    private final boolean isTombstone;
    private final byte[] data;

    private Value(byte[] data, long timestamp, boolean isTombstone) {
        this.data = data == null ? null : Arrays.copyOf(data, data.length);
        this.timestamp = timestamp;
        this.isTombstone = isTombstone;
    }

    public Value(byte[] data, long timestamp) {
        this(data, timestamp, false);
    }

    public static Value tombstone(long timestamp) {
        return new Value(null, timestamp, true);
    }

    public static Value fromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        long timestamp = buffer.getLong();
        boolean isTombstone = buffer.get() == 1;
        byte[] value = new byte[buffer.remaining()];

        if (!isTombstone) {
            buffer.get(value);
        }

        return new Value(value, timestamp, isTombstone);
    }

    public byte[] toBytes() {
        ByteBuffer buffer =
                ByteBuffer.allocate(Long.BYTES + Byte.BYTES + (data == null ? 0 : data.length))
                        .putLong(timestamp)
                        .put((byte) (isTombstone ? 1 : 0));

        if (data != null) {
            buffer.put(data);
        }

        return buffer.array();
    }

    public byte[] value() {
        return data;
    }

    public long timestamp() {
        return timestamp;
    }

    public boolean isTombstone() {
        return isTombstone;
    }
}
