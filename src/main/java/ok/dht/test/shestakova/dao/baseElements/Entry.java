package ok.dht.test.shestakova.dao.baseElements;

public interface Entry<D> {
    D key();

    D value();

    default boolean isTombstone() {
        return value() == null;
    }
}
