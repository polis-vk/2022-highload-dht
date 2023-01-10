package ok.dht.test.shestakova.dao.base;

public interface Entry<D> {
    D key();

    D value();

    long timestamp();

    default boolean isTombstone() {
        return value() == null;
    }
}
