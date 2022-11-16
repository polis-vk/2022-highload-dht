package ok.dht.test.shik.streaming;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Chunk {

    private static final int CHUNK_SIZE = 1024;
    private static final byte[] DELIMITER = "\n".getBytes(StandardCharsets.UTF_8);

    private final ByteBuffer buffer;
    private int size;

    public Chunk() {
        buffer = ByteBuffer.allocate(CHUNK_SIZE);
        size = 0;
    }

    public boolean add(byte[] key, byte[] value) {
        if (key.length + value.length + 2 * DELIMITER.length + size > CHUNK_SIZE) {
            return false;
        }

        buffer.put(size, key);
        size += key.length;
        buffer.put(size, DELIMITER);
        size += DELIMITER.length;
        buffer.put(size, value);
        size += value.length;
        return true;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public byte[] getBytes() {
        return Arrays.copyOf(buffer.array(), size);
    }

    public int getSize() {
        return size;
    }
}
