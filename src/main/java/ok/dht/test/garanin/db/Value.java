package ok.dht.test.garanin.db;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Value {
    private final long timestamp;
    private final boolean tombstone;
    private final byte[] data;

    private Value(@Nonnull byte[] data, long timestamp, boolean tombstone) {
        this.data = Arrays.copyOf(data, data.length);
        this.timestamp = timestamp;
        this.tombstone = tombstone;
    }

    public Value(byte[] data, long timestamp) {
        this(data, timestamp, false);
    }

    public Value(byte[] value) {
        if (value == null || value.length == 0) {
            this.data = new byte[0];
            this.timestamp = 0;
            this.tombstone = true;
        } else {
            ByteBuffer buffer = ByteBuffer.wrap(value);

            this.timestamp = buffer.getLong();
            this.tombstone = buffer.get() == 1;
            this.data = new byte[buffer.remaining()];
            if (data.length > 0) {
                buffer.get(data);
            }
        }
    }

    public Value(long timestamp) {
        this(new byte[0], timestamp, true);
    }

    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + Byte.BYTES + data.length)
                .putLong(timestamp)
                .put((byte) (tombstone ? 1 : 0));

        if (data.length > 0) {
            buffer.put(data);
        }

        return buffer.array();
    }

    public byte[] data() {
        return Arrays.copyOf(data, data.length);
    }

    public long timestamp() {
        return timestamp;
    }

    public boolean tombstone() {
        return tombstone;
    }
}
