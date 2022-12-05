package ok.dht.test.ushkov.dao;

import org.rocksdb.RocksDB;
import org.rocksdb.RocksIterator;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;

public class EntryIterator implements Iterator<Entry> {
    private final RocksIterator rocksIterator;
    private final byte[] to;

    public EntryIterator(RocksDB db, byte[] from, byte[] to) {
        this.rocksIterator = db.newIterator();
        this.to = Arrays.copyOf(to, to.length);
        rocksIterator.seek(from);
    }

    public EntryIterator(RocksDB db, byte[] from) {
        this.rocksIterator = db.newIterator();
        this.to = null;
        rocksIterator.seek(from);
    }

    public EntryIterator(RocksDB db) {
        this.rocksIterator = db.newIterator();
        this.to = null;
        rocksIterator.seekToFirst();
    }

    @Override
    public boolean hasNext() {
        if (!rocksIterator.isValid()) {
            return false;
        }
        if (to != null) {
            String key = new String(rocksIterator.key(), StandardCharsets.UTF_8);
            String toString = new String(to, StandardCharsets.UTF_8);
            return key.compareTo(toString) < 0;
        } else {
            return true;
        }
    }

    @Override
    public Entry next() {
        Entry entry = Entry.newEntry(rocksIterator.key(), rocksIterator.value());
        rocksIterator.next();
        return entry;
    }
}
