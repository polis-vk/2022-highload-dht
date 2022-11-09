package ok.dht.test.maximenko;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.maximenko.dao.BaseEntry;
import ok.dht.test.maximenko.dao.Dao;
import ok.dht.test.maximenko.dao.Entry;

import java.io.IOException;
import java.nio.ByteBuffer;

class ValueAndTime {
    byte[] value;
    long time;

    public ValueAndTime(byte[] value, long time) {
        this.value = value;
        this.time = time;
    }
}

public class TimeDaoWrapper {
    private final Dao dao;

    public TimeDaoWrapper(Dao dao) {
        this.dao = dao;
    }

    void put(byte[] keyBytes, byte[] valueBytes, long time) {
        MemorySegment key = MemorySegment.ofArray(keyBytes);
        ByteBuffer valueWithTime = ByteBuffer.allocate(Long.BYTES + Short.BYTES + valueBytes.length);
        valueWithTime.putLong(time);
        valueWithTime.putShort((short) 1);
        valueWithTime.put(valueBytes);
        MemorySegment value = MemorySegment.ofArray(valueWithTime.array());
        Entry entry = new BaseEntry(key, value);
        dao.upsert(entry);
    }

    void delete(byte[] keyBytes, long time) {
        MemorySegment key = MemorySegment.ofArray(keyBytes);
        ByteBuffer valueWithTime = ByteBuffer.allocate(Long.BYTES + Short.BYTES);
        valueWithTime.putLong(time);
        valueWithTime.putShort((short) 0);
        MemorySegment value = MemorySegment.ofArray(valueWithTime.array());
        Entry entry = new BaseEntry(key, value);
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
        short haveValue =  valueWithTime.getShort();
        if (haveValue == 0) {
            return new ValueAndTime(null, time);
        }
        byte[] valueWithoutTime = new byte[valueWithTime.remaining()];
        valueWithTime.get(valueWithoutTime);
        return new ValueAndTime(valueWithoutTime, time);
    }
}
