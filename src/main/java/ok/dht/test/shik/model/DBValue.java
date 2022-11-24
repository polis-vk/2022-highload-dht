package ok.dht.test.shik.model;

import javax.annotation.Nullable;

import java.util.Comparator;

@SuppressWarnings("PMD.ArrayIsStoredDirectly")
public class DBValue {

    public static final Comparator<DBValue> COMPARATOR = (v1, v2) -> {
        if (v1.timestamp != v2.timestamp) {
            return v1.timestamp < v2.timestamp ? -1 : 1;
        }
        if (v1.value == null && v2.value == null) {
            return 0;
        }
        if (v1.value == null) {
            return -1;
        }
        if (v2.value == null) {
            return 1;
        }
        if (v1.value.length != v2.value.length) {
            return v1.value.length - v2.value.length;
        }
        for (int i = 0; i < v1.value.length; ++i) {
            if (v1.value[i] != v2.value[i]) {
                return v1.value[i] - v2.value[i];
            }
        }
        return 0;
    };

    @Nullable
    private final byte[] value;

    private final long timestamp;

    public DBValue(@Nullable byte[] value, long timestamp) {
        this.value = value;
        this.timestamp = timestamp;
    }

    @SuppressWarnings("PMD.MethodReturnsInternalArray")
    @Nullable
    public byte[] getValue() {
        return value;
    }

    public long getTimestamp() {
        return timestamp;
    }

}
