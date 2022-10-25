package ok.dht.test.anikina.dao;

public record BaseEntry<Data>(Data key, Data value, Data timestamp) implements Entry<Data> {
    @Override
    public String toString() {
        return "{" + key + ":" + value + ":" + timestamp + "}";
    }
}
