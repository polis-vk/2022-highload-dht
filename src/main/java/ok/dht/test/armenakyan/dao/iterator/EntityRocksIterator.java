package ok.dht.test.armenakyan.dao.iterator;

import ok.dht.test.armenakyan.dao.model.Entity;
import one.nio.util.Utf8;
import org.rocksdb.RocksIterator;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Iterator;

public class EntityRocksIterator implements Iterator<Entity> {
    private final RocksIterator iterator;
    private final byte[] toKey;

    public EntityRocksIterator(RocksIterator iterator, String fromInclusive, @Nullable String toExclusive) {
        this.iterator = iterator;
        this.toKey = toExclusive == null ? null : Utf8.toBytes(toExclusive);

        iterator.seek(Utf8.toBytes(fromInclusive));
    }

    @Override
    public boolean hasNext() {
        return iterator.isValid() && !Arrays.equals(iterator.key(), toKey);
    }

    @Override
    public Entity next() {
        Entity result = new Entity(iterator.key(), iterator.value());

        iterator.next();

        return result;
    }
}
