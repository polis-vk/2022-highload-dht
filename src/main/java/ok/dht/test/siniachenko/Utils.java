package ok.dht.test.siniachenko;

import java.nio.ByteBuffer;

public class Utils {
    public static byte[] withCurrentTimestampAndFlagDeleted(byte[] value, boolean flagDeleted) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(value.length + 9);
        byte flag;
        if (flagDeleted) {
            flag = 0b0;
        } else {
            flag = 0b1;
        }
        byteBuffer.put(flag);
        byteBuffer.put(value);
        long millis = System.currentTimeMillis();
        byteBuffer.putLong(millis);

        byte[] newValue = new byte[value.length + 9];
        byteBuffer.position(0);
        byteBuffer.get(newValue);

        return newValue;
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
        ByteBuffer newByteBuffer = ByteBuffer.allocate(value.length + 9);
        newByteBuffer.put(value);
        newByteBuffer.position(value.length - 8);
        return newByteBuffer.getLong();
    }

    public static byte[] readValueFromBytes(byte[] codedValue) {
        byte[] value = new byte[codedValue.length - 9];
        System.arraycopy(codedValue, 1, value, 0, value.length);
        return value;
    }
}
