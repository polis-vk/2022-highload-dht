package ok.dht.test.skroba.dao.base;

public interface Entry<D> {
    D key();
    
    D value();
    
    default boolean isTombstone() {
        return value() == null;
    }
}
