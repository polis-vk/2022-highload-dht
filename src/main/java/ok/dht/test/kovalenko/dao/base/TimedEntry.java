package ok.dht.test.kovalenko.dao.base;

public interface TimedEntry<D> {

    long timestamp();

    D key();

    D value();

    default boolean isTombstone() {
        return value() == null;
    }
}
