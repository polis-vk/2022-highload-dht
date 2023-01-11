package ok.dht.test.maximenko;

import java.util.Arrays;

class ValueAndTime {
    final byte[] value;
    final long time;

    public ValueAndTime(byte[] value, long time) {
        if (value == null) {
            this.value = null;
        } else {
            this.value = Arrays.copyOf(value, value.length);
        }
        this.time = time;
    }
}

