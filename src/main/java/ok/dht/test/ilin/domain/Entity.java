package ok.dht.test.ilin.domain;

import java.io.Serializable;

public class Entity implements Serializable {
    private long timestamp;
    private boolean tombstone;
    private byte[] data;

    public Entity(long timestamp, boolean tombstone, byte[] data) {
        this.timestamp = timestamp;
        this.tombstone = tombstone;
        this.data = data;
    }

    public Entity() {
        timestamp = Long.MIN_VALUE;
        tombstone = false;
        data = new byte[0];
    }

    public long timestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean tombstone() {
        return tombstone;
    }

    public void setTombstone(boolean tombstone) {
        this.tombstone = tombstone;
    }

    public byte[] data() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}
