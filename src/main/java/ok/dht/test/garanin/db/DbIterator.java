package ok.dht.test.garanin.db;

import one.nio.util.Utf8;
import org.rocksdb.RocksIterator;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class DbIterator {

    private static final byte[] delimiter = Utf8.toBytes("\r\n");

    private final RocksIterator iterator;
    @Nullable
    private final String to;

    public DbIterator(RocksIterator iterator, @Nullable String to) {
        this.iterator = iterator;
        this.to = to;
    }

    private boolean isValid() {
        return iterator.isValid()
                && (to == null || new String(iterator.key(), StandardCharsets.UTF_8).compareTo(to) < 0);
    }

    private KeyValue curr() {
        var key = iterator.key();
        var value = iterator.value();
        return new KeyValue(key, new Value(value));
    }

    private int chuckedSize() {
        return curr().size() + delimiter.length * 2 + Utf8.toBytes(Integer.toHexString(curr().size())).length;
    }

    public boolean fillBuffer(ByteBuffer buffer) {
        buffer.clear();
        if (!isValid()) {
            buffer.put(Utf8.toBytes("0"));
            buffer.put(delimiter);
            buffer.put(delimiter);
            return false;
        }
        while (isValid() && buffer.remaining() > 0 && chuckedSize() <= buffer.remaining()) {
            buffer.put(Utf8.toBytes(Integer.toHexString(curr().size())));
            buffer.put(delimiter);
            buffer.put(curr().toBytes());
            buffer.put(delimiter);
            iterator.next();
        }
        return true;
    }
}
