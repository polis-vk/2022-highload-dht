package ok.dht.test.yasevich;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.yasevich.dao.BaseEntry;
import ok.dht.test.yasevich.dao.Dao;
import ok.dht.test.yasevich.dao.Entry;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class TimeStampingDao {

    private final Dao<MemorySegment, Entry<MemorySegment>> dao;

    public TimeStampingDao(Dao<MemorySegment, Entry<MemorySegment>> dao) {
        this.dao = dao;
    }

    public TimeStampedValue get(String key) {
        Entry<MemorySegment> entry;
        try {
            entry = dao.get(memSegmentOfString(key));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (entry == null) {
            return null;
        }
        return TimeStampedValue.fromBytes(entry.value().toByteArray());
    }

    public void upsert(String key, @Nullable byte[] value, long time) {
        TimeStampedValue timeStampedValue = new TimeStampedValue(value, time);
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

    static class TimeStampedValue {
        public final byte[] value;
        public final long time;

        TimeStampedValue(byte[] value, long time) {
            this.value = value == null ? null : Arrays.copyOf(value, value.length); //codeclimate fix
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
