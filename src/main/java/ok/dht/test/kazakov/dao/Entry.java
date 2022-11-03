package ok.dht.test.kazakov.dao;

public interface Entry<D> {
    D getKey();

    D getValue();

    long getTimestamp();

    byte[] getValueBytes();

    default boolean isTombstone() {
        return getValue() == null;
    }
}
