package ok.dht.test.kiselyov.dao;

public interface Entry<D, E> {
    D key();

    D value();

    E timestamp();

    default boolean isTombstone() {
        return value() == null;
    }
}
