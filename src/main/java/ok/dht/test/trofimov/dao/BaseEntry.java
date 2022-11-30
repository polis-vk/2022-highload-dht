package ok.dht.test.trofimov.dao;

public record BaseEntry<Data>(Data key, Data value, long timestamp) implements Entry<Data> {
    @Override
    public String toString() {
        return "{" + key + ":" + value + "}";
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }
}
