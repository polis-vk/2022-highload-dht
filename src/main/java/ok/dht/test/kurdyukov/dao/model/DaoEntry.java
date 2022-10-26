package ok.dht.test.kurdyukov.dao.model;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.time.Instant;

public class DaoEntry implements Comparable<DaoEntry>, Serializable {
    private final Instant timestamp;

    public final byte[] value;

    public DaoEntry(Instant timestamp, byte[] value) {
        this.timestamp = timestamp;
        this.value = value;
    }

    @Override
    public int compareTo(@Nonnull DaoEntry daoEntry) {
        return timestamp.compareTo(daoEntry.timestamp);
    }
}
