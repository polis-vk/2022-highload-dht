package ok.dht.test.siniachenko.range;

import ok.dht.test.siniachenko.Utils;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.util.ByteArrayBuilder;
import one.nio.util.Utf8;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;

public class EntityChunkStreamQueueItem extends Session.QueueItem {

    private final ByteBuffer terminatingChunkByteBuffer = ByteBuffer.wrap(
        Utf8.toBytes(Integer.toHexString(0) + "\r\n\r\n")
    );

    private final Iterator<Map.Entry<byte[], byte[]>> entityIterator;
    private final boolean separatorAfterValue;
    private final boolean valueWithMeta;
    private ByteBuffer tempChunkByteBuffer;

    public EntityChunkStreamQueueItem(
        Iterator<Map.Entry<byte[], byte[]>> entityIterator,
        boolean separatorAfterValue, boolean valueWithMeta, byte[] metaData
    ) {
        this.entityIterator = entityIterator;
        this.separatorAfterValue = separatorAfterValue;
        this.valueWithMeta = valueWithMeta;
        tempChunkByteBuffer = ByteBuffer.wrap(metaData);
    }

    private boolean hasRemaining() {
        return tempChunkByteBuffer != null;
    }

    @Override
    public int remaining() {
        return hasRemaining() ? 1 : 0;
    }

    @Override
    public int write(Socket socket) throws IOException {
        if (hasRemaining()) {
            return writeFromBuffer(socket);
        } else {
            return 0;
        }
    }

    private void nextByteBuffer() {
        if (entityIterator.hasNext()) {
            Map.Entry<byte[], byte[]> next = entityIterator.next();
            byte[] chunk = getEntityChunk(next);
            tempChunkByteBuffer = ByteBuffer.wrap(chunk);
        } else {
            tempChunkByteBuffer = terminatingChunkByteBuffer;
        }
    }

    private byte[] getEntityChunk(Map.Entry<byte[], byte[]> entity) {
        ByteArrayBuilder chunkBuilder = new ByteArrayBuilder();
        byte[] key = entity.getKey();
        byte[] value;
        if (valueWithMeta) {
            value = entity.getValue();
        } else {
            value = Utils.readValueFromBytes(entity.getValue());
        }
        int chunkLength = key.length + 1 + value.length;
        if (separatorAfterValue) {
            chunkLength += 10;
        }
        chunkBuilder.append(Integer.toHexString(chunkLength))
            .append('\r')
            .append('\n')
            .append(key)
            .append('\n')
            .append(value);
        if (separatorAfterValue) {
            chunkBuilder.append('\n')
                .append('\n')
                .append('\n')
                .append('\n')
                .append('\n')
                .append('\n')
                .append('\n')
                .append('\n')
                .append('\n')
                .append('\n');
        }
        chunkBuilder.append('\r')
            .append('\n');
        return chunkBuilder.toBytes();
    }

    private int writeFromBuffer(Socket socket) throws IOException {
        int written = socket.write(tempChunkByteBuffer);
        if (!tempChunkByteBuffer.hasRemaining()) {
            if (tempChunkByteBuffer.equals(terminatingChunkByteBuffer)) {
                // That was last buffer with terminating chunk
                tempChunkByteBuffer = null;
            } else {
                nextByteBuffer();
            }
        }
        return written;
    }
}
