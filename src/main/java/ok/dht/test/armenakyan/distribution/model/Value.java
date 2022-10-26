package ok.dht.test.armenakyan.distribution.model;

import java.nio.ByteBuffer;

public class Value {
    private final long timestamp;
    private final boolean isTombstone;
    private final byte[] value;


    private Value(byte[] value, long timestamp, boolean isTombstone) {
        this.value = value;
        this.timestamp = timestamp;
        this.isTombstone = isTombstone;
    }

    public Value(byte[] value, long timestamp) {
        this(value, timestamp, false);
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
                ByteBuffer.allocate(Long.BYTES + Byte.BYTES + (value == null ? 0 : value.length))
                        .putLong(timestamp)
                        .put((byte) (isTombstone ? 1 : 0));

        if (value != null) {
            buffer.put(value);
        }

        return buffer.array();
    }

    public byte[] value() {
        return value;
    }

    public long timestamp() {
        return timestamp;
    }

    public boolean isTombstone() {
        return isTombstone;
    }
}
