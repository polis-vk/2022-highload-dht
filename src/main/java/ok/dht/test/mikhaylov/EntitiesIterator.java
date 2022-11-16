package ok.dht.test.mikhaylov;

import one.nio.util.ByteArrayBuilder;
import org.apache.log4j.Logger;
import org.rocksdb.RocksIterator;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;

public class EntitiesIterator implements Iterator<byte[]> {
    private final @Nullable byte[] end;

    private final RocksIterator iterator;

    private final static Logger log = Logger.getLogger(EntitiesIterator.class);

    public EntitiesIterator(RocksIterator iterator, String start, @Nullable String end) {
        this.end = end == null ? null : end.getBytes(StandardCharsets.UTF_8);
        this.iterator = iterator;
        this.iterator.seek(start.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean hasNext() {
        return iterator.isValid() && (end == null || Arrays.compareUnsigned(iterator.key(), end) < 0);
    }

    @Override
    public byte[] next() {
        byte[] key = iterator.key();
        log.error("key: " + new String(key, StandardCharsets.UTF_8));
        byte[] value = DatabaseUtilities.getValue(iterator.value());
        iterator.next();
        return new ByteArrayBuilder(key.length + 1 + value.length)
                .append(key)
                .append((byte) '\n')
                .append(value)
                .toBytes();
    }
}
