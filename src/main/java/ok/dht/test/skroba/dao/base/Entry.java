package ok.dht.test.skroba.dao.base;

import java.io.Serializable;

public interface Entry<D> extends Serializable {
    D key();
    
    D value();
    
    long timeStamp();
    
    default boolean isTombstone() {
        return value() == null;
    }
}
