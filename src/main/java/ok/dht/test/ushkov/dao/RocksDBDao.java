package ok.dht.test.ushkov.dao;

import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.nio.ByteBuffer;

public class RocksDBDao {
    public final RocksDB db;

    public RocksDBDao(RocksDB db) {
        this.db = db;
    }

    public RocksDB getDb() {
        return db;
    }

    public Entry get(byte[] key, long timestamp) throws RocksDBException {
        byte[] value = db.get(key);
        Entry entry = Entry.newEntry(key, value);
        if (entry.ttl() != 0 && entry.timestamp() + entry.ttl() < timestamp) {
            // ttl expired
            return new Entry(key, null, entry.timestamp() + entry.ttl(), 0);
        } else {
            return entry;
        }
    }

    public void put(byte[] key, byte[] value, long timestamp, long ttl) throws RocksDBException {
        ByteBuffer buffer = ByteBuffer.allocate(2 * Long.BYTES + 1 + value.length);
        buffer.putLong(timestamp);
        buffer.putLong(ttl);
        buffer.put((byte) 0); // tombstone
        buffer.put(2 * Long.BYTES + 1, value);

        Entry entry = Entry.newEntry(key, db.get(key));
        if (entry.timestamp() > timestamp) {
            return;
        }

        db.put(key, buffer.array());
    }

    public void delete(byte[] key, long timestamp) throws RocksDBException {
        ByteBuffer buffer = ByteBuffer.allocate(2 * Long.BYTES + 1);
        buffer.putLong(timestamp);
        buffer.putLong(0); // ttl(forever)
        buffer.put((byte) 1); // tombstone

        Entry entry = Entry.newEntry(key, db.get(key));
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

    public EntryIterator range() {
        return new EntryIterator(db);
    }
}
