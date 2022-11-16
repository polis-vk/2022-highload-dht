package ok.dht.test.vihnin.database;

import java.util.Iterator;

public interface DataBase<K, V> {

    V get(K key);

    Iterator<Row<K, V>> getRange(K start, K end);

    boolean put(K key, V value);

    boolean delete(K key);

    void close();
}
