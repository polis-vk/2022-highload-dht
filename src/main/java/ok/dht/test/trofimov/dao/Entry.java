package ok.dht.test.trofimov.dao;

public interface Entry<D> {
    D key();

    D value();

    long getTimestamp();

    default boolean isTombstone() {
        return value() == null;
    }
}
