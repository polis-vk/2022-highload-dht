package ok.dht.test.shakhov.dao;

public record BaseEntry<Data>(Data key, long timestamp, Data value) implements Entry<Data> {
    @Override
    public String toString() {
        return "{" + key + ":" + value + "}";
    }
}
