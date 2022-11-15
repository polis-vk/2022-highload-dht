package ok.dht.test.ushkov.dao;

import java.nio.ByteBuffer;

public record Entry(byte[] key, byte[] value, long timestamp) {
    public static Entry newEntry(byte[] key, byte[] entry) {
        if (entry == null) {
            return new Entry(key, null, 0);
        }

        ByteBuffer buffer = ByteBuffer.wrap(entry);

        long timestamp = buffer.getLong();
        byte tombstone = buffer.get();

        if (tombstone == 1) {
            return new Entry(key, null, timestamp);
        }

        byte[] value = new byte[buffer.remaining()];
        buffer.get(Long.BYTES + 1, value);
        return new Entry(key, value, timestamp);
    }
}
