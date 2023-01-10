package ok.dht.test.kurdyukov.dao.repository;

import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.iq80.leveldb.DBIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

import static org.iq80.leveldb.impl.Iq80DBFactory.bytes;

public class DaoRepository implements Iterable<Map.Entry<byte[], byte[]>> {
    private static final Logger logger = LoggerFactory.getLogger(DaoRepository.class);

    private final DB levelDB;

    public DaoRepository(DB levelDB) {
        this.levelDB = levelDB;
    }

    public void close() {
        try {
            levelDB.close();
        } catch (IOException e) {
            logger.error("Fail db close.", e);

            throw new RuntimeException(e);
        }
    }

    public byte[] get(String id) {
        try {
            return levelDB.get(bytes(id));
        } catch (DBException e) {
            logger.error("Fail on get method with id: " + id, e);

            throw new RuntimeException(e);
        }
    }

    public void put(String id, byte[] daoEntry) {
        try {
            levelDB.put(bytes(id), daoEntry);
        } catch (DBException e) {
            logger.error("Fail on put method with id: " + id, e);

            throw new RuntimeException(e);
        }
    }

    @Override
    public DBIterator iterator() {
        return levelDB.iterator();
    }
}
