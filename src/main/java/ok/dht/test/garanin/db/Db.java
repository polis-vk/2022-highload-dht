package ok.dht.test.garanin.db;

import one.nio.util.Utf8;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Db {
    private static RocksDB rocksDB;

    static {
        RocksDB.loadLibrary();
    }

    private Db() {
    }

    public static void open(Path workingDir) throws DbException {
        try {
            var dbPath = workingDir.resolve("rocksdb");
            Files.createDirectories(dbPath);
            rocksDB = RocksDB.open(dbPath.toAbsolutePath().normalize().toString());
        } catch (IOException | RocksDBException e) {
            throw new DbException(e);
        }
    }

    public static void close() throws DbException {
        try {
            rocksDB.closeE();
            rocksDB = null;
        } catch (RocksDBException e) {
            throw new DbException(e);
        }
    }

    public static byte[] get(String key) throws DbException {
        try {
            return rocksDB.get(Utf8.toBytes(key));
        } catch (RocksDBException e) {
            throw new DbException(e);
        }
    }

    public static void put(String key, byte[] value) throws DbException {
        try {
            rocksDB.put(Utf8.toBytes(key), value);
        } catch (RocksDBException e) {
            throw new DbException(e);
        }
    }

    public static void delete(String key) throws DbException {
        try {
            rocksDB.delete(Utf8.toBytes(key));
        } catch (RocksDBException e) {
            throw new DbException(e);
        }
    }
}
