package ok.dht.test.kovalenko.dao.base;

public record BaseTimedEntry<Data>(long timestamp, Data key, Data value) implements TimedEntry<Data> {
    @Override
    public String toString() {
        return "{time = " + timestamp + ", key = " + key + ", value = " + value + "}";
    }
}
