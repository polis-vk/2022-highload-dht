package ok.dht.test.siniachenko.service;

import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.util.Utf8;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;

public class EntityStreamQueueItem extends Session.QueueItem {

    private final Iterator<Map.Entry<byte[], byte[]>> entityIterator;
    private ByteBuffer tempByteBuffer;

    public EntityStreamQueueItem(Iterator<Map.Entry<byte[], byte[]>> entityIterator) {
        this.entityIterator = entityIterator;
    }

    @Override
    public int remaining() {
        return entityIterator.hasNext() ? 1 : 0;
    }

    @Override
    public int write(Socket socket) throws IOException {
        if (tempByteBuffer == null) {
            nextByteBuffer();
            return 0;
        } else {
            return writeFromBuffer(socket);
        }
    }

    private void nextByteBuffer() {
        Map.Entry<byte[], byte[]> next = entityIterator.next();
        String key = Utf8.toString(next.getKey());
        String value = Utf8.toString(next.getValue());
        byte[] bytes = Utf8.toBytes(String.format("%s\n%s", key, value));
        tempByteBuffer = ByteBuffer.wrap(bytes);
    }

    private int writeFromBuffer(Socket socket) throws IOException {
        int written = socket.write(tempByteBuffer);
        if (!tempByteBuffer.hasRemaining()) {
            tempByteBuffer = null;
        }
        return written;
    }
}
