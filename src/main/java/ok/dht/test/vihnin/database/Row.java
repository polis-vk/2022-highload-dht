package ok.dht.test.vihnin.database;

import java.util.Objects;

public class Row<K, V> {
    private final K key;
    private final V value;

    public Row(K key, V value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Row<?, ?> row = (Row<?, ?>) o;
        return Objects.equals(key, row.key) && Objects.equals(value, row.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }
}
