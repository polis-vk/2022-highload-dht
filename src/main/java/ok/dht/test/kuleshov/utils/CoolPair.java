package ok.dht.test.kuleshov.utils;

public class CoolPair<K, V> {
    private final K first;
    private final V second;

    public CoolPair(K first, V second) {
        this.first = first;
        this.second = second;
    }

    public K getFirst() {
        return first;
    }

    public V getSecond() {
        return second;
    }
}
