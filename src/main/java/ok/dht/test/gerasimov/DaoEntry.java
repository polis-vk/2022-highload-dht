package ok.dht.test.gerasimov;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.Arrays;

public class DaoEntry implements Comparable<DaoEntry>, Serializable {
    private final Long timestamp;
    private final byte[] value;
    private boolean isTombstone;

    public DaoEntry(Long timestamp, byte[] value) {
        this.timestamp = timestamp;
        this.value = value != null ? Arrays.copyOf(value, value.length) : new byte[0];
    }

    public DaoEntry(Long timestamp, byte[] value, boolean isTombstone) {
        this.timestamp = timestamp;
        this.value = value != null ? Arrays.copyOf(value, value.length) : new byte[0];
        this.isTombstone = isTombstone;
    }

    public void setTombstone(boolean tombstone) {
        isTombstone = tombstone;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public byte[] getValue() {
        return Arrays.copyOf(value, value.length);
    }

    public boolean isTombstone() {
        return isTombstone;
    }

    @Override
    public int compareTo(@Nonnull DaoEntry daoEntry) {
        return timestamp.compareTo(daoEntry.timestamp);
    }
}
