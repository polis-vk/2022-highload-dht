package ok.dht.test.frolovm.artyomdrozdov;

public interface Entry<D> {
    D key();

    D value();

    default boolean isTombstone() {
        return value() == null;
    }
}
