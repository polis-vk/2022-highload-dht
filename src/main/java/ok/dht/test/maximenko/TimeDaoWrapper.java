package ok.dht.test.maximenko;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.maximenko.dao.BaseEntry;
import ok.dht.test.maximenko.dao.Entry;
import ok.dht.test.maximenko.dao.MemorySegmentDao;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;

public class TimeDaoWrapper {
    private final MemorySegmentDao dao;

    public TimeDaoWrapper(MemorySegmentDao dao) {
        this.dao = dao;
    }

    void put(byte[] keyBytes, byte[] valueBytes, long time) {
        MemorySegment key = MemorySegment.ofArray(keyBytes);
        ByteBuffer valueWithTime = ByteBuffer.allocate(Long.BYTES + Short.BYTES + valueBytes.length);
        valueWithTime.putLong(time);
        valueWithTime.putShort((short) 1);
        valueWithTime.put(valueBytes);
        MemorySegment value = MemorySegment.ofArray(valueWithTime.array());
        Entry<MemorySegment> entry = new BaseEntry<>(key, value);
        dao.upsert(entry);
    }

    void delete(byte[] keyBytes, long time) {
        MemorySegment key = MemorySegment.ofArray(keyBytes);
        ByteBuffer valueWithTime = ByteBuffer.allocate(Long.BYTES + Short.BYTES);
        valueWithTime.putLong(time);
        valueWithTime.putShort((short) 0);
        MemorySegment value = MemorySegment.ofArray(valueWithTime.array());
        Entry<MemorySegment> entry = new BaseEntry<>(key, value);
        dao.upsert(entry);
    }

    ValueAndTime get(byte[] keyBytes) {
        MemorySegment key = MemorySegment.ofArray(keyBytes);
        Entry<MemorySegment> value;
        try {
            value = dao.get(key);
        } catch (IOException e) {
            return null;
        }
        if (value == null) {
            return null;
        }
        if (value.value() == null) {
            return null;
        }
        ByteBuffer valueWithTime = value.value().asByteBuffer();
        long time = valueWithTime.getLong();
        short haveValue = valueWithTime.getShort();
        if (haveValue == 0) {
            return new ValueAndTime(null, time);
        }
        byte[] valueWithoutTime = new byte[valueWithTime.remaining()];
        valueWithTime.get(valueWithoutTime);
        return new ValueAndTime(valueWithoutTime, time);
    }

    public Iterator<Entry<MemorySegment>> get(MemorySegment start, MemorySegment end) throws IOException {
        return new Iterator<>() {
            final Iterator<Entry<MemorySegment>> daoIterator = dao.get(start, end);
            @Override
            public boolean hasNext() {
                return daoIterator.hasNext();
            }

            @Override
            public Entry<MemorySegment> next() {
                Entry<MemorySegment> entry = daoIterator.next();
                if (entry == null) {
                    return null;
                }
                ByteBuffer valueWithTime = entry.value().asByteBuffer();
                valueWithTime.getLong(); //time
                short haveValue = valueWithTime.getShort();
                if (haveValue == 0) {
                    return new BaseEntry<>(entry.key(), null);
                }

                byte[] valueWithoutTime = new byte[valueWithTime.remaining()];
                valueWithTime.get(valueWithoutTime);
                return new BaseEntry<>(entry.key(), MemorySegment.ofArray(valueWithoutTime));
            }
        };
    }
}
