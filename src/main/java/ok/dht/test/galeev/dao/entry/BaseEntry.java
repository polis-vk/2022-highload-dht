package ok.dht.test.galeev.dao.entry;

public record BaseEntry<K, V>(K key, V value) implements Entry<K, V> {
    @Override
    public String toString() {
        return "{" + key + ":" + value + "}";
    }
}
