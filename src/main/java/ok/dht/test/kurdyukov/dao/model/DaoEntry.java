package ok.dht.test.kurdyukov.dao.model;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.time.Instant;

public class DaoEntry implements Comparable<DaoEntry>, Serializable {
    private final Instant timestamp;

    public final byte[] value;
    public final boolean isTombstone;

    public DaoEntry(Instant timestamp, byte[] value, boolean isTombstone) {
        this.timestamp = timestamp;
        this.value = value;
        this.isTombstone = isTombstone;
    }

    @Override
    public int compareTo(@Nonnull DaoEntry daoEntry) {
        return timestamp.compareTo(daoEntry.timestamp);
    }
}
