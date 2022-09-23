package ok.dht.test.ushkov.dao.rocksdb;

import ok.dht.test.ushkov.dao.BaseEntry;
import ok.dht.test.ushkov.dao.Dao;
import ok.dht.test.ushkov.dao.Entry;
import org.rocksdb.FlushOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;

public class RocksDBDao implements Dao<ByteBuffer, Entry<ByteBuffer>> {
    private final RocksDB db;

    public RocksDBDao(String path) throws IOException {
        try {
            db = RocksDB.open(path);
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }

    @Override
    public Iterator<Entry<ByteBuffer>> get(ByteBuffer from, ByteBuffer to) throws IOException {
        return new RocksDBIterator(db, from, to);
    }

    @Override
    public Entry<ByteBuffer> get(ByteBuffer key) throws IOException {
        try {
            byte[] value = db.get(key.array());
            if (value == null) {
                return null;
            }
            return new BaseEntry<>(
                key,
                ByteBuffer.wrap(value)
            );
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }

    @Override
    public Iterator<Entry<ByteBuffer>> allFrom(ByteBuffer from) throws IOException {
        return new RocksDBIterator(db, from, null);
    }

    @Override
    public Iterator<Entry<ByteBuffer>> allTo(ByteBuffer to) throws IOException {
        return new RocksDBIterator(db, null, to);
    }

    @Override
    public Iterator<Entry<ByteBuffer>> all() throws IOException {
        return new RocksDBIterator(db, null, null);
    }

    @Override
    public void upsert(Entry<ByteBuffer> entry) throws IOException {
        try {
            if (entry.isTombstone()) {
                db.delete(entry.key().array());
            } else {
                db.put(entry.key().array(), entry.value().array());
            }
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void flush() throws IOException {
        try {
            db.flush(new FlushOptions());
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void compact() throws IOException {
        // Do nothing
    }

    @Override
    public void close() throws IOException {
        try {
            flush();
            db.closeE();
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }
}
