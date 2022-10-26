package ok.dht.test.gerasimov;

import javax.annotation.Nonnull;
import java.io.Serializable;

public class DaoEntry implements Comparable<DaoEntry>, Serializable {
    private final Long timestamp;
    private final byte[] value;
    private boolean isTombstone = false;


    public DaoEntry(Long timestamp, byte[] value) {
        this.timestamp = timestamp;
        this.value = value;
    }

    public DaoEntry(Long timestamp, byte[] value, boolean isTombstone) {
        this.timestamp = timestamp;
        this.value = value;
        this.isTombstone = isTombstone;
    }

    public void setTombstone(boolean tombstone) {
        isTombstone = tombstone;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public byte[] getValue() {
        return value;
    }

    public boolean isTombstone() {
        return isTombstone;
    }

    @Override
    public int compareTo(@Nonnull DaoEntry daoEntry) {
        return timestamp.compareTo(daoEntry.timestamp);
    }
}