package ok.dht.test.siniachenko;

import java.nio.ByteBuffer;

public final class Utils {
    private Utils() {

    }

    public static byte[] withCurrentTimestampAndFlagDeleted(byte[] value, boolean flagDeleted) {
        // value + timestamp + flag deleted
        int size = value.length + Long.BYTES + 1;
        byte[] newValue = new byte[size];
        ByteBuffer byteBuffer = ByteBuffer.wrap(newValue);

        // flag deleted
        byte flag;
        if (flagDeleted) {
            flag = 0b0;
        } else {
            flag = 0b1;
        }
        byteBuffer.put(flag);

        // array
        byteBuffer.put(value);

        // timestamp
        long millis = System.currentTimeMillis();
        byteBuffer.putLong(millis);

        return byteBuffer.array();
    }

    public static byte[] setDeleted(byte[] value, boolean flagDeleted) {
        if (flagDeleted) {
            value[0] = 0b0;
        } else {
            value[0] = 0b1;
        }
        return value;
    }

    public static boolean readFlagDeletedFromBytes(byte[] value) {
        return value[0] == 0b0;
    }

    public static long readTimeMillisFromBytes(byte[] value) {
        ByteBuffer newByteBuffer = ByteBuffer.wrap(value);
        int timeMillisPosition = 1;  // after flag deleted
        return newByteBuffer.getLong(timeMillisPosition);
    }

    public static byte[] readValueFromBytes(byte[] codedValue) {
        byte[] value = new byte[codedValue.length - 9];
        System.arraycopy(codedValue, 1, value, 0, value.length);
        return value;
    }
}
