package ok.dht.test.skroba.dao.base;

public record BaseEntry<Data>(Data key, Data value, long timeStamp) implements Entry<Data> {
    @Override
    public String toString() {
        return "{" + key + ":" + value + "}";
    }
}
