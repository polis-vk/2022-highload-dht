package ok.dht.test.anikina.dao;

public interface Entry<D> {
    D key();

    D value();

    D timestamp();

    default boolean isTombstone() {
        return value() == null;
    }
}
