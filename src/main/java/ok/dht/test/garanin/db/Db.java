package ok.dht.test.garanin.db;

import one.nio.util.Utf8;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Db {

    static {
        RocksDB.loadLibrary();
    }

    private Db() {
    }

    public static RocksDB open(Path workingDir) throws DbException {
        try {
            var dbPath = workingDir.resolve("rocksdb");
            Files.createDirectories(dbPath);
            return RocksDB.open(dbPath.toAbsolutePath().normalize().toString());
        } catch (IOException | RocksDBException e) {
            throw new DbException(e);
        }
    }

    public static void close(RocksDB rocksDB) throws DbException {
        try {
            rocksDB.closeE();
        } catch (RocksDBException e) {
            throw new DbException(e);
        }
    }

    public static byte[] get(RocksDB rocksDB, String key) throws DbException {
        try {
            return rocksDB.get(Utf8.toBytes(key));
        } catch (RocksDBException e) {
            throw new DbException(e);
        }
    }

    public static void put(RocksDB rocksDB, String key, byte[] value) throws DbException {
        try {
            rocksDB.put(Utf8.toBytes(key), value);
        } catch (RocksDBException e) {
            throw new DbException(e);
        }
    }

    public static void delete(RocksDB rocksDB, String key) throws DbException {
        try {
            rocksDB.delete(Utf8.toBytes(key));
        } catch (RocksDBException e) {
            throw new DbException(e);
        }
    }
}
