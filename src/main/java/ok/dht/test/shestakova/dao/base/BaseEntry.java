package ok.dht.test.shestakova.dao.base;

public record BaseEntry<Data>(Data key, Data value, long timestamp) implements Entry<Data> {
    @Override
    public String toString() {
        return "{" + key + ":" + value + "}";
    }
}
