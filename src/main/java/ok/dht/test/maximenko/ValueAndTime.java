package ok.dht.test.maximenko;

import java.util.Arrays;

class ValueAndTime {
    final byte[] value;
    final long time;

    public ValueAndTime(byte[] value, long time) {
        if (value != null) {
            this.value = Arrays.copyOf(value, value.length);
        } else {
            this.value = null;
        }
        this.time = time;
    }
}

