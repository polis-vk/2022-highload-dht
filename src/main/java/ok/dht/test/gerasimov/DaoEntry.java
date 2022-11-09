package ok.dht.test.gerasimov;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.Arrays;

public class DaoEntry implements Comparable<DaoEntry>, Serializable {
    private final Long timestamp;
    private final byte[] value;
    private boolean isTombstone;

    public DaoEntry(Long timestamp, byte[] value) {
        this.timestamp = timestamp;
        if (value == null) {
            this.value = new byte[0];
        } else {
            this.value = Arrays.copyOf(value, value.length);
        }
    }

    public DaoEntry(Long timestamp, byte[] value, boolean isTombstone) {
        this.timestamp = timestamp;
        if (value == null) {
            this.value = new byte[0];
        } else {
            this.value = Arrays.copyOf(value, value.length);
        }
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
