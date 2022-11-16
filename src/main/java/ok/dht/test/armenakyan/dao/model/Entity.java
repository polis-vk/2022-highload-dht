package ok.dht.test.armenakyan.dao.model;

import one.nio.util.Utf8;

import java.util.Arrays;

public class Entity {
    private final byte[] key;
    private final Value value;

    public Entity(byte[] key, Value value) {
        this.key = Arrays.copyOf(key, key.length);
        this.value = value;
    }

    public Entity(byte[] key, byte[] value) {
        this(key, Value.fromBytes(value));
    }

    public Entity(String key, Value value) {
        this(Utf8.toBytes(key), value);
    }

    public Value value() {
        return value;
    }

    public byte[] key() {
        return Arrays.copyOf(key, key.length);
    }

    public int size() {
        return key.length + value.size();
    }

    public int rawSize() {
        return key.length + value.rawSize();
    }
}
