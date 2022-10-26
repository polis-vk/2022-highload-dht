package ok.dht.test.yasevich;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.yasevich.dao.BaseEntry;
import ok.dht.test.yasevich.dao.Dao;
import ok.dht.test.yasevich.dao.Entry;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;

public class TimeStampingDao {

    private final Dao<MemorySegment, Entry<MemorySegment>> dao;

    public TimeStampingDao(Dao<MemorySegment, Entry<MemorySegment>> dao) {
        this.dao = dao;
    }

    public TimeStampedValue get(String key) throws IOException {
        Entry<MemorySegment> entry = dao.get(memSegmentOfString(key));
        if (entry == null) {
            return null;
        }
        return TimeStampedValue.fromBytes(entry.value().toByteArray());
    }

    public void upsertTimeStamped(String key, @Nullable byte[] value) {
        TimeStampedValue timeStampedValue = new TimeStampedValue(value, System.currentTimeMillis());
        MemorySegment memorySegmentValue = MemorySegment.ofArray(timeStampedValue.wholeToBytes());
        dao.upsert(new BaseEntry<>(memSegmentOfString(key), memorySegmentValue));
    }

    static byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);
        return buffer.array();
    }

    private static MemorySegment memSegmentOfString(String data) {
        return MemorySegment.ofArray(data.toCharArray());
    }

    public void close() throws IOException {
        dao.close();
    }

    static final class TimeStampedValue {
        public final byte[] value;
        public final long time;

        TimeStampedValue(byte[] value, long time) {
            this.value = value;
            this.time = time;
        }

        public static TimeStampedValue fromBytes(byte[] bytes) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            long time = buffer.getLong();
            boolean valuePresent = buffer.get() == 1;
            if (!valuePresent) {
                return new TimeStampedValue(null, time);
            }
            byte[] value = new byte[buffer.remaining()];
            buffer.get(value);
            return new TimeStampedValue(value, time);
        }

        public static TimeStampedValue tombstoneFromTime(byte[] timeBytes) {
            ByteBuffer buffer = ByteBuffer.wrap(timeBytes);
            long time = buffer.getLong();
            return new TimeStampedValue(null, time);
        }

        public byte[] wholeToBytes() {
            int valueLength = value == null ? 0 : value.length;
            byte[] timeStampedValue = new byte[Long.BYTES + Byte.BYTES + valueLength];

            ByteBuffer buff = ByteBuffer.wrap(timeStampedValue);
            byte valuePresented = value == null ? (byte) 0 : 1;
            buff.put(longToBytes(time));
            buff.put(valuePresented);
            if (value != null) {
                buff.put(value);
            }
            return buff.array();
        }

        public byte[] valueBytes() {
            ByteBuffer buffer = ByteBuffer.allocate(value.length);
            buffer.put(value);
            return buffer.array();
        }

        public byte[] timeBytes() {
            return longToBytes(time);
        }
    }

}
