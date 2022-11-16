package ok.dht.test.shestakova;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.shestakova.dao.base.BaseEntry;
import one.nio.net.Session;
import one.nio.net.Socket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class MyQueueItem extends Session.QueueItem {
    private static final int BUFFER_CAPACITY = 1024;
    private final byte[] DELIMITER = "\n".getBytes(StandardCharsets.UTF_8);
    private final byte[] EO_LINE = "\r\n".getBytes(StandardCharsets.UTF_8);
    private final byte[] EO_MSG = "0\r\n\r\n".getBytes(StandardCharsets.UTF_8);

    private final Iterator<BaseEntry<MemorySegment>> entryIterator;
    private final ByteBuffer buffer = ByteBuffer.allocate(BUFFER_CAPACITY);

    public MyQueueItem(ChunkedResponse response) {
        this.entryIterator = response.getEntryIterator();
    }

    @Override
    public int remaining() {
        return entryIterator.hasNext() ? 1 : 0;
    }

    @Override
    public int write(Socket socket) throws IOException {
        if (entryIterator.hasNext()) {
            BaseEntry<MemorySegment> entry = entryIterator.next();
            int size = getSize(entry.key().asByteBuffer(), entry.value().asByteBuffer());

            if (size > buffer.remaining()) {
                int res = writeBufferToSocket(socket);
                putEntryToBuffer(entry);
                return res;
            }
            putEntryToBuffer(entry);
        }

        if (!entryIterator.hasNext()) {
            if (EO_MSG.length > buffer.remaining()) {
                writeBufferToSocket(socket);
            }
            buffer.put(EO_MSG);
            return writeBufferToSocket(socket);
        }
        return 0;
    }

    private void putEntryToBuffer(BaseEntry<MemorySegment> entry) {
        ByteBuffer key = entry.key().asByteBuffer();
        ByteBuffer value = entry.value().asByteBuffer();

        int size = getSize(key, value);
        byte[] hexSize = Integer.toHexString(size).getBytes(StandardCharsets.UTF_8);

        buffer.put(hexSize);
        buffer.put(EO_LINE);
        buffer.put(key);
        buffer.put(DELIMITER);
        buffer.put(value);
        buffer.put(EO_LINE);
    }

    private int writeBufferToSocket(Socket socket) throws IOException {
        buffer.flip();
        int res = socket.write(buffer);
        buffer.clear();
        return res;
    }

    private int getSize(ByteBuffer key, ByteBuffer value) {
        return key.limit() + DELIMITER.length + value.limit();
    }
}
