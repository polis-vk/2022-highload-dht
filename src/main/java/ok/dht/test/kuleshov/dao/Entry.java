package ok.dht.test.kuleshov.dao;

public interface Entry<D> {
    D key();

    D value();

    long timestamp();

    default boolean isTombstone() {
        return value() == null;
    }
}
