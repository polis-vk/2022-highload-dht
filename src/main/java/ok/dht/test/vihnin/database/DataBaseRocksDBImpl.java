package ok.dht.test.vihnin.database;

import one.nio.util.Utf8;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.nio.file.Path;

public class DataBaseRocksDBImpl implements DataBase<String, byte[]> {

    private RocksDB actualDataBase;

    public DataBaseRocksDBImpl(Path path) throws RocksDBException {
        this.actualDataBase = RocksDB.open(path.toString());
    }

    @Override
    public byte[] get(String key) {
        try {
            return actualDataBase.get(Utf8.toBytes(key));
        } catch (Throwable e) {
            return null;
        }
    }

    @Override
    public boolean put(String key, byte[] value) {
        try {
            actualDataBase.put(Utf8.toBytes(key), value);
        } catch (Throwable e) {
            return false;
        }
        return true;
    }

    @Override
    public boolean delete(String key) {
        try {
            actualDataBase.delete(Utf8.toBytes(key));
        } catch (Throwable e) {
            return false;
        }
        return true;
    }

    @Override
    public void close() {
        actualDataBase.close();
        actualDataBase = null;
    }
}
