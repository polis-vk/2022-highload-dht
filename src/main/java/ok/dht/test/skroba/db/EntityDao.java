package ok.dht.test.skroba.db;

import ok.dht.test.skroba.db.exception.DaoException;
import org.iq80.leveldb.DBIterator;

import java.io.Closeable;

public interface EntityDao extends Closeable {
    byte[] get(String key) throws DaoException;
    
    void put(String key, byte[] entity) throws DaoException;
    
    DBIterator iterator() throws DaoException;
    
    void open();
    
    void close();
}
