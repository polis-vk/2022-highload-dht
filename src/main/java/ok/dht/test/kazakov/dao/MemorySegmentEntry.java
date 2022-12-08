package ok.dht.test.kazakov.dao;

import jdk.incubator.foreign.MemorySegment;

import java.util.Objects;

@SuppressWarnings("ClassCanBeRecord")
public class MemorySegmentEntry implements Entry<MemorySegment> {
    private final MemorySegment key;
    private final MemorySegment value;
    private final long timestamp;

    public MemorySegmentEntry(final MemorySegment key, final MemorySegment value, final long timestamp) {
        this.key = key;
        this.value = value;
        this.timestamp = timestamp;
    }

    @Override
    public MemorySegment getKey() {
        return key;
    }

    @Override
    public MemorySegment getValue() {
        return value;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public byte[] getValueBytes() {
        return value.toByteArray();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof MemorySegmentEntry that)) return false;

        if (timestamp != that.timestamp) return false;
        if (!Objects.equals(key, that.key)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        int result = key == null ? 0 : key.hashCode();
        result = 31 * result + (value == null ? 0 : value.hashCode());
        result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "{" + key + ":" + value + "[" + timestamp + "]}";
    }
}
