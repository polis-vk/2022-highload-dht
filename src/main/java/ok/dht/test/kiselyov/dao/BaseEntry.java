package ok.dht.test.kiselyov.dao;

public record BaseEntry<D, E>(D key, D value, E timestamp) implements Entry<D, E> {
    @Override
    public String toString() {
        return "{" + key + ":" + value + "}";
    }
}
