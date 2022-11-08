package ok.dht.test.galeev.dao.entry;

public interface Entry<K,V> {
    K key();

    V value();

    default boolean isTombstone() {
        return value() == null;
    }
}
