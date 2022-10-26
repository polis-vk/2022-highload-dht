package ok.dht.test.panov.dao;

public interface Entry<D> {
    D key();

    D value();

    long timestamp();

    default boolean isTombstone() {
        return value() == null;
    }
}
