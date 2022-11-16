package ok.dht.test.vihnin.database;

import one.nio.util.Utf8;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;

public class DataBaseRocksDBImpl implements DataBase<String, byte[]> {

    private RocksDB actualDataBase;

    public DataBaseRocksDBImpl(Path path) throws RocksDBException {
        this.actualDataBase = RocksDB.open(path.toString());
    }

    @Override
    public byte[] get(String key) {
        try {
            return actualDataBase.get(Utf8.toBytes(key));
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean put(String key, byte[] value) {
        try {
            actualDataBase.put(Utf8.toBytes(key), value);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public boolean delete(String key) {
        try {
            actualDataBase.delete(Utf8.toBytes(key));
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public void close() {
        actualDataBase.close();
        actualDataBase = null;
    }

    @Override
    public Iterator<Row<String, byte[]>> getRange(String start, String end) {
        var iterator = actualDataBase.newIterator();
        iterator.seek(Utf8.toBytes(start));

        var endBytes = end == null ? null : Utf8.toBytes(end);

        return new Iterator<>() {

            @Override
            public boolean hasNext() {
                if (!iterator.isValid()) return false;
                var currentKey = iterator.key();

                if (end == null) {
                    return true;
                } else {
                    return Arrays.compare(currentKey, endBytes) < 0;
                }
            }

            @Override
            public Row<String, byte[]> next() {
                var nextValue = new Row<>(Utf8.toString(iterator.key()), iterator.value());
                iterator.next();
                return nextValue;
            }
        };
    }
}
