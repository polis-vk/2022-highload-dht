package ok.dht.test.kuleshov.dao;

public record BaseEntry<Data>(Data key, Data value, long timestamp) implements Entry<Data> {
    @Override
    public String toString() {
        return "{" + key + ":" + value + System.lineSeparator() + "timestamp:" + timestamp + "}";
    }

    @Override
    public long timestamp() {
        return timestamp;
    }
}
