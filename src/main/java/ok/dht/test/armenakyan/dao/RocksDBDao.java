package ok.dht.test.armenakyan.dao;

import ok.dht.test.armenakyan.dao.iterator.EntityRocksIterator;
import ok.dht.test.armenakyan.dao.model.Value;
import one.nio.util.Utf8;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import javax.annotation.Nullable;
import java.nio.file.Path;

public class RocksDBDao implements DhtDao {
    private final RocksDB rocksDB;

    public RocksDBDao(Path dbWorkingDir) throws DaoException {
        try {
            rocksDB = RocksDB.open(dbWorkingDir.toString());
        } catch (RocksDBException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public Value get(String key) throws DaoException {
        try {
            byte[] bytes = rocksDB.get(Utf8.toBytes(key));

            if (bytes == null) {
                return Value.tombstone(0);
            }

            return Value.fromBytes(bytes);
        } catch (RocksDBException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public void put(String key, byte[] value, long timestamp) throws DaoException {
        try {
            Value val = new Value(value, timestamp);
            rocksDB.put(Utf8.toBytes(key), val.toBytes());
        } catch (RocksDBException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public void delete(String key, long timestamp) throws DaoException {
        try {
            rocksDB.put(Utf8.toBytes(key), Value.tombstone(timestamp).toBytes());
        } catch (RocksDBException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public EntityRocksIterator range(String fromInclusive, String toExclusive) {
        return rangeImpl(fromInclusive, toExclusive);
    }

    @Override
    public EntityRocksIterator range(String fromInclusive) {
        return rangeImpl(fromInclusive, null);
    }

    private EntityRocksIterator rangeImpl(String fromInclusive, @Nullable String toExclusive) {
        RocksIterator rocksIterator = rocksDB.newIterator();

        return new EntityRocksIterator(rocksIterator, fromInclusive, toExclusive);
    }

    public void close() throws DaoException {
        try {
            rocksDB.closeE();
        } catch (RocksDBException e) {
            throw new DaoException(e);
        }
    }
}
