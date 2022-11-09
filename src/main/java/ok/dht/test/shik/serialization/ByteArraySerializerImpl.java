package ok.dht.test.shik.serialization;

import ok.dht.test.shik.model.DBValue;

import java.nio.ByteBuffer;

public class ByteArraySerializerImpl implements ByteArraySerializer {

    private static final int VERSION = 1;

    @Override
    public byte[] serialize(DBValue dbValue) {
        byte[] value = dbValue.getValue();
        ByteBuffer buffer = ByteBuffer.allocate(getSize(dbValue));
        int pos = 0;
        buffer.putInt(pos, ByteArraySerializerFactory.LATEST_VERSION);
        pos += Integer.BYTES;

        if (value == null) {
            buffer.put(pos, (byte) 1);
            pos += Byte.BYTES;
        } else {
            buffer.put(pos, (byte) 0);
            pos += Byte.BYTES;
            buffer.putInt(pos, value.length);
            pos += Integer.BYTES;
            buffer.put(pos, value);
            pos += value.length * Byte.BYTES;
        }

        buffer.putLong(pos, dbValue.getTimestamp());
        return buffer.array();
    }

    @Override
    public DBValue deserialize(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int pos = 0;
        int version = buffer.getInt(pos);
        pos += Integer.BYTES;
        if (version != VERSION) {
            throw new IllegalArgumentException("Cannot deserialize value, expected version = "
                + VERSION + "found version = " + version);
        }

        byte nullable = buffer.get(pos);
        pos += Byte.BYTES;

        byte[] value = null;
        if (nullable == 0) {
            int length = buffer.getInt(pos);
            pos += Integer.BYTES;
            value = new byte[length];
            buffer.get(pos, value, 0, length);
            pos += length * Byte.BYTES;
        }

        long timestamp = buffer.getLong(pos);
        return new DBValue(value, timestamp);
    }

    private int getSize(DBValue dbValue) {
        int size = Integer.BYTES;
        size += Byte.BYTES;
        if (dbValue.getValue() != null) {
            size += Integer.BYTES;
            size += dbValue.getValue().length * Byte.BYTES;
        }
        size += Long.BYTES;
        return size;
    }
}
