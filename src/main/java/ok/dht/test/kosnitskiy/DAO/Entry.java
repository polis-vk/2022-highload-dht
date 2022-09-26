package ok.dht.test.kosnitskiy.DAO;

public interface Entry<D> {
    D key();

    D value();

    default boolean isTombstone() {
        return value() == null;
    }
}
