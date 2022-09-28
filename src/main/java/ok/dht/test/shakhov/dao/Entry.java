package ok.dht.test.shakhov.dao;

public interface Entry<D> {
    D key();

    D value();

    default boolean isTombstone() {
        return value() == null;
    }
}
