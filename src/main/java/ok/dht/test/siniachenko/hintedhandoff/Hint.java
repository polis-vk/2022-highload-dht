package ok.dht.test.siniachenko.hintedhandoff;

public class Hint {
    private final byte[] key;
    private final byte[] value;

    public Hint(byte[] key, byte[] value) {
        this.key = key;
        this.value = value;
    }

    public byte[] getKey() {
        return key;
    }

    public byte[] getValue() {
        return value;
    }
}
