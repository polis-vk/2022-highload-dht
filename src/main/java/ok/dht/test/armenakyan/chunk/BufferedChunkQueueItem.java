package ok.dht.test.armenakyan.chunk;

import ok.dht.test.armenakyan.dao.model.Entity;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.util.ByteArrayBuilder;
import one.nio.util.Utf8;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;

public class BufferedChunkQueueItem extends Session.QueueItem {
    private static final int MAX_BUFFER_SIZE_BYTES = 4 << 10; // 4KB
    public static final byte[] SEPARATOR = Utf8.toBytes("\r\n");
    public static final byte[] IN_CHUNK_SEPARATOR = Utf8.toBytes("\n");
    public static final ByteBuffer END_CHUNK_BUFFER = ByteBuffer.wrap(
            new ByteArrayBuilder(1 + 2 * SEPARATOR.length)
                    .append('0')
                    .append(SEPARATOR)
                    .append(SEPARATOR)
                    .toBytes()
    );

    public final Iterator<Entity> entitiesIterator;
    private final ByteBuffer writeBuffer = ByteBuffer.allocate(MAX_BUFFER_SIZE_BYTES);
    private ByteBuffer currentBuffer;
    private boolean iteratorHasRemaining = true;

    public BufferedChunkQueueItem(ChunkedResponse response) {
        this.entitiesIterator = response.entitiesIterator();
    }

    @Override
    public int remaining() {
        return iteratorHasRemaining || currentBuffer != null ? 1 : 0;
    }

    @Override
    public int write(Socket socket) throws IOException {
        if (!writeBuffer.hasRemaining()) { // clear buffer if it's fully written to socket
            writeBuffer.clear();
        }

        if (writeBuffer.position() == 0) { // fill buffer for write if it's empty
            fillBuffer();
            writeBuffer.flip();
        }

        return socket.write(writeBuffer); // write buffer to socket (possibly partially)
    }

    private void fillBuffer() {
        do {
            if (currentBuffer != null) {
                writeBuffer.put(currentBuffer);
                currentBuffer = null;
            }

            if (!iteratorHasRemaining) {
                break;
            }

            if (entitiesIterator.hasNext()) {
                currentBuffer = encodeChunk(entitiesIterator.next());
            } else {
                iteratorHasRemaining = false;
                currentBuffer = END_CHUNK_BUFFER.clear();
            }

        } while (writeBuffer.remaining() > currentBuffer.remaining());
    }

    private static ByteBuffer encodeChunk(Entity entity) {
        byte[] key = entity.key();
        byte[] value = entity.value().rawData();

        int chunkSize = entity.rawSize() + IN_CHUNK_SEPARATOR.length;
        byte[] chunkSizeByte = Utf8.toBytes(Integer.toHexString(chunkSize));

        ByteBuffer chunkBuffer = ByteBuffer.allocate(
                chunkSizeByte.length
                        + chunkSize
                        + SEPARATOR.length * 2
        );

        return chunkBuffer
                .put(chunkSizeByte)
                .put(SEPARATOR)
                .put(key)
                .put(IN_CHUNK_SEPARATOR)
                .put(value)
                .put(SEPARATOR)
                .flip();
    }
}
