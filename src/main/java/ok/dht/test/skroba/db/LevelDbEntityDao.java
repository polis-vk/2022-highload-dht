package ok.dht.test.skroba.db;

import ok.dht.test.skroba.db.exception.DaoException;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.iq80.leveldb.impl.Iq80DBFactory.factory;

public class LevelDbEntityDao implements EntityDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(LevelDbEntityDao.class);
    
    private final Path path;
    private DB db;
    
    public LevelDbEntityDao(final Path path) {
        this.path = path;
    }
    
    @Override
    public byte[] get(final String key) throws DaoException {
        try {
            return db.get(key.getBytes(StandardCharsets.UTF_8));
        } catch (DBException e) {
            LOGGER.error("Can't get entity from level db with key: " + key, e);
            
            throw new DaoException("Can't get entity from level db with key: " + key, e);
        }
    }
    
    @Override
    public void put(final String key, final byte[] entity) throws DaoException {
        try {
            db.put(key.getBytes(StandardCharsets.UTF_8), entity);
        } catch (DBException e) {
            LOGGER.error("Can't put entity in level db with key: " + key, e);
            
            throw new DaoException("Can't put entity in level db with key: " + key, e);
        }
    }
    
    @Override
    public DBIterator iterator() throws DaoException {
        return db.iterator();
    }
    
    @Override
    public void open() {
        Options options = new Options();
        options.createIfMissing(true);
        
        try {
            this.db = factory.open(path.toFile(), options);
        } catch (IOException e) {
            LOGGER.error("Can't open level db: ", e);
            
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public void close() {
        try {
            this.db.close();
        } catch (IOException e) {
            LOGGER.error("Can't close level db: ", e);
            
            throw new RuntimeException(e);
        }
    }
}
