package ok.dht.test.ilin.repository.impl;

import ok.dht.ServiceConfig;
import ok.dht.test.ilin.model.Entity;
import ok.dht.test.ilin.repository.EntityRepository;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class RocksDBEntityRepositoryImpl implements EntityRepository {

    private final RocksDB rocksDB;
    private final Logger logger;

    public RocksDBEntityRepositoryImpl(ServiceConfig serviceConfig) throws IOException {
        this.logger = LoggerFactory.getLogger(RocksDBEntityRepositoryImpl.class);
        RocksDB.loadLibrary();
        final Options options = new Options();
        options.setCreateIfMissing(true);
        try {
            this.rocksDB = RocksDB.open(options, serviceConfig.workingDir().toString());
        } catch (RocksDBException ex) {
            logger.error("Error initializing RocksDB");
            throw new IOException(ex);
        }
    }

    @Override
    public void upsert(Entity value) {
        try {
            rocksDB.put(value.id().getBytes(StandardCharsets.UTF_8), value.value());
        } catch (RocksDBException e) {
            logger.error("Error saving entry in RocksDB");
        }
    }

    @Override
    public Entity get(String id) {
        try {
            byte[] result = rocksDB.get(id.getBytes(StandardCharsets.UTF_8));
            if (result == null) {
                return null;
            }
            return new Entity(id, result);
        } catch (RocksDBException e) {
            logger.error("Error get entry in RocksDB");
        }
        return null;
    }

    @Override
    public void delete(String id) {
        try {
            rocksDB.delete(id.getBytes(StandardCharsets.UTF_8));
        } catch (RocksDBException e) {
            logger.error("Error delete entry in RocksDB");
        }
    }

    @Override
    public void close() {
        rocksDB.close();
    }
}
