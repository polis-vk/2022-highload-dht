package ok.dht.test.garanin.db;

import one.nio.util.ByteArrayBuilder;
import one.nio.util.Utf8;

public record KeyValue(byte[] key, Value value) {
    private static final byte[] delimiter = Utf8.toBytes("\n");

    public int size() {
        return key.length + delimiter.length + value.data().length;
    }

    public byte[] toBytes() {
        return new ByteArrayBuilder(size()).append(key).append(delimiter).append(value.data()).toBytes();
    }
}
