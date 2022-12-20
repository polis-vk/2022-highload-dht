package ok.dht.test.komissarov.database.models;

public record BaseEntry<Data>(Data key, Data value, long timestamp) implements Entry<Data> {
    @Override
    public String toString() {
        return "{" + key + ":" + value + ", " + timestamp + "}";
    }
}
