package ok.dht.test.ushkov.dao;

import java.nio.ByteBuffer;

public record Entry(byte[] key, byte[] value, long timestamp, long ttl) {
    public static Entry newEntry(byte[] key, byte[] entry) {
        if (entry == null) {
            return new Entry(key, null, 0, 0);
        }

        ByteBuffer buffer = ByteBuffer.wrap(entry);

        long timestamp = buffer.getLong();
        long ttl = buffer.getLong();
        byte tombstone = buffer.get();

        if (tombstone == 1) {
            return new Entry(key, null, timestamp, ttl);
        }

        byte[] value = new byte[buffer.remaining()];
        buffer.get(2 * Long.BYTES + 1, value);
        return new Entry(key, value, timestamp, ttl);
    }
}
