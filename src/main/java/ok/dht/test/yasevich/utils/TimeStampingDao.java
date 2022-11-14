package ok.dht.test.yasevich.utils;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.yasevich.dao.BaseEntry;
import ok.dht.test.yasevich.dao.Dao;
import ok.dht.test.yasevich.dao.Entry;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;

public class TimeStampingDao {

    private final Dao<MemorySegment, Entry<MemorySegment>> dao;

    public TimeStampingDao(Dao<MemorySegment, Entry<MemorySegment>> dao) {
        this.dao = dao;
    }

    public Iterator<Entry<byte[]>> get(String start, String end) throws IOException {
        MemorySegment endSegment = end == null ? null : memSegmentOfString(end);
        Iterator<Entry<MemorySegment>> entries = dao.get(memSegmentOfString(start), endSegment);
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return entries.hasNext();
            }

            @Override
            public Entry<byte[]> next() {
                Entry<MemorySegment> next = entries.next();
                return new BaseEntry<>(next.key().toByteArray(),
                        TimeStampedValue.fromBytes(next.value().toByteArray()).value);
            }
        };
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

    private static byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);
        return buffer.array();
    }

    private static MemorySegment memSegmentOfString(String data) {
        return MemorySegment.ofArray(data.getBytes(StandardCharsets.UTF_8));
    }

    public void close() throws UncheckedIOException {
        try {
            dao.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class TimeStampedValue {
        public final byte[] value;
        public final long time;

        public TimeStampedValue(byte[] value, long time) {
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
            return value;
        }

        public byte[] timeBytes() {
            return longToBytes(time);
        }
    }

}
