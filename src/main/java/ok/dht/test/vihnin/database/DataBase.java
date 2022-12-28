package ok.dht.test.vihnin.database;

public interface DataBase<K, V> {

    V get(K key);

    boolean put(K key, V value);

    boolean delete(K key);

    void close();
}
