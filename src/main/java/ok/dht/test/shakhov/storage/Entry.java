package ok.dht.test.shakhov.storage;

public interface Entry<D> {
    D key();

    D value();

    default boolean isTombstone() {
        return value() == null;
    }
}
