package ok.dht.test.siniachenko;

public class Utils {
    public static byte[] withCurrentTimestampAndNotDeletedFlag(byte[] value, boolean flagDeleted) {
        byte[] newValue = new byte[value.length + 9];
        if (flagDeleted) {
            newValue[0] = 0b0;
        } else {
            newValue[0] = 0b1;
        }
        System.arraycopy(value, 0, newValue, 1, value.length);
        long currentTimeMillis = System.currentTimeMillis();
        for (int i = 0; i < 8; ++i) {
            newValue[value.length + 1 + i] = (byte) (currentTimeMillis << (8 * i));
        }
        return newValue;
    }

    public static boolean readFlagDeletedFromBytes(byte[] value) {
        return value[0] == 0b0;
    }

    public static long readTimeMillisFromBytes(byte[] value) {
        long timeMillisFromLastBytes = 0;
        for (int i = 0; i < 8; ++i) {
            timeMillisFromLastBytes += ((long) value[value.length - 8 + i]) >> (8 * i);
        }
        return timeMillisFromLastBytes;
    }

    public static byte[] readValueFromBytes(byte[] codedValue) {
        byte[] value = new byte[codedValue.length - 9];
        System.arraycopy(codedValue, 1, value, 0, value.length);
        return value;
    }

}
