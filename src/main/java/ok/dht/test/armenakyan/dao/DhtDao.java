package ok.dht.test.armenakyan.dao;

import ok.dht.test.armenakyan.dao.model.Entity;
import ok.dht.test.armenakyan.dao.model.Value;

import java.util.Iterator;

public interface DhtDao {
    Value get(String key) throws DaoException;

    void put(String key, byte[] value, long timestamp) throws DaoException;

    void delete(String key, long timestamp) throws DaoException;

    Iterator<Entity> range(String fromInclusive, String toExclusive) throws DaoException;

    Iterator<Entity> range(String fromInclusive) throws DaoException;
}
