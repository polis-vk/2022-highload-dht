package ok.dht.test.ushkov.dao;

import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import java.nio.ByteBuffer;
import java.util.List;

public class RocksDBDao {
    public final RocksDB db;

    public RocksDBDao(RocksDB db) {
        this.db = db;
    }

    public RocksDB getDb() {
        return db;
    }

    public Entry get(byte[] key) throws RocksDBException {
        byte[] entry = db.get(key);
        return Entry.newEntry(key, entry);
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

    public EntryIterator range(byte[] from, byte[] to) {
        return new EntryIterator(db, from, to);
    }

    public EntryIterator range(byte[] from) {
        return new EntryIterator(db, from);
    }
}
