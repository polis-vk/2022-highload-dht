package ok.dht.test.panov.dao;

public record BaseEntry<Data>(Data key, Data value, long timestamp) implements Entry<Data> {
    @Override
    public String toString() {
        return "BaseEntry{"
                + "key=" + key
                + ", value=" + value
                + ", timestamp=" + timestamp
                + '}';
    }
}
