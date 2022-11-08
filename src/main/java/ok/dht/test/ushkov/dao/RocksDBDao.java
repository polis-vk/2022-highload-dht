package ok.dht.test.ushkov.dao;

import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.nio.ByteBuffer;

public class RocksDBDao {
    private final RocksDB db;

    public RocksDBDao(RocksDB db) {
        this.db = db;
    }

    public RocksDB getDb() {
        return db;
    }

    public Entry get(byte[] key) throws RocksDBException {
        byte[] entry = db.get(key);

        if (entry == null) {
            return new Entry(null, 0);
        }

        ByteBuffer buffer = ByteBuffer.wrap(entry);

        long timestamp = buffer.getLong();
        byte tombstone = buffer.get();

        if (tombstone == 1) {
            return new Entry(null, timestamp);
        }

        byte[] value = new byte[buffer.remaining()];
        buffer.get(Long.BYTES, value);
        return new Entry(value, timestamp);
    }

    public void put(byte[] key, byte[] value, long timestamp) throws RocksDBException {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + 1 + value.length);
        buffer.putLong(timestamp);
        buffer.put((byte) 0);
        buffer.put(Long.BYTES, value);

        Entry entry = get(key);
        if (entry.timestamp() > timestamp) {
            return;
        }

        db.put(key, buffer.array());
    }

    public void delete(byte[] key, long timestamp) throws RocksDBException {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + 1);
        buffer.putLong(timestamp);
        // tombstone
        buffer.put((byte) 1);

        Entry entry = get(key);
        if (entry.timestamp() > timestamp) {
            return;
        }

        db.put(key, buffer.array());
    }
}
