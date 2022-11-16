package ok.dht.test.armenakyan.dao.iterator;

import ok.dht.test.armenakyan.dao.model.Entity;
import one.nio.util.Utf8;
import org.rocksdb.RocksIterator;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class EntityRocksIterator implements Iterator<Entity> {
    private final RocksIterator iterator;
    private final String toKey;

    public EntityRocksIterator(RocksIterator iterator, String fromInclusive, @Nullable String toExclusive) {
        this.iterator = iterator;
        this.toKey = toExclusive;

        iterator.seek(Utf8.toBytes(fromInclusive));
    }

    @Override
    public boolean hasNext() {
        if (!iterator.isValid()) {
            return false;
        }

        String currentKey = new String(iterator.key(), StandardCharsets.UTF_8);

        return toKey == null || currentKey.compareTo(toKey) < 0;
    }

    @Override
    public Entity next() {
        Entity result = new Entity(iterator.key(), iterator.value());

        iterator.next();

        return result;
    }
}
