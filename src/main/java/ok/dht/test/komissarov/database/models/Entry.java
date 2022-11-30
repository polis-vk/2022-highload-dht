package ok.dht.test.komissarov.database.models;

public interface Entry<D> {
    D key();

    D value();

    long timestamp();

    default boolean isTombstone() {
        return value() == null;
    }
}
