package ok.dht.test.ushkov.dao.rocksdb;

import ok.dht.test.ushkov.dao.BaseEntry;
import ok.dht.test.ushkov.dao.Entry;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksIterator;

import java.nio.ByteBuffer;
import java.util.Iterator;

public class RocksDBIterator implements Iterator<Entry<ByteBuffer>> {
    private final RocksIterator rocksIterator;
    private final ByteBuffer to;

    public RocksDBIterator(RocksDB db, ByteBuffer from, ByteBuffer to) {
        rocksIterator = db.newIterator();
        if (from == null) {
            rocksIterator.seekToFirst();
        } else {
            rocksIterator.seek(from);
        }
        this.to = to;
    }

    @Override
    public boolean hasNext() {
        return !lastReached() && rocksIterator.isValid();
    }

    private boolean lastReached() {
        ByteBuffer key = ByteBuffer.wrap(rocksIterator.key());
        return key.equals(to);
    }

    @Override
    public Entry<ByteBuffer> next() {
        ByteBuffer key = ByteBuffer.wrap(rocksIterator.key());
        ByteBuffer value = ByteBuffer.wrap(rocksIterator.value());
        rocksIterator.next();
        return new BaseEntry<>(key, value);
    }
}
