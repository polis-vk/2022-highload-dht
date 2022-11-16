package ok.dht.test.kiselyov.util;

import ok.dht.test.kiselyov.dao.BaseEntry;
import one.nio.net.Session;
import one.nio.net.Socket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class IterableQueueItem extends Session.QueueItem {
    private static final int BUFFER_CAPACITY = 1048576;
    private static final String DELIMITER = "\n";
    private static final String CRLF = "\r\n";
    private static final String FINAL_BYTES = "0\r\n\r\n";
    private final Iterator<BaseEntry<byte[], Long>> entriesIterator;
    private final ByteBuffer buffer = ByteBuffer.allocate(BUFFER_CAPACITY);
    private boolean isFinalBytesShouldBeWritten;
    public IterableQueueItem(Iterator<BaseEntry<byte[], Long>> entriesIterator, ChunkedResponse response) {
        this.entriesIterator = entriesIterator;
        byte[] headers = response.toBytes(false);
        buffer.put(headers, 0, headers.length);
    }

    @Override
    public int remaining() {
        return entriesIterator.hasNext() || isFinalBytesShouldBeWritten ? 1 : 0;
    }

    @Override
    public int write(Socket socket) throws IOException {
        if (entriesIterator.hasNext()) {
            BaseEntry<byte[], Long> entry = entriesIterator.next();
            int entrySize = getEntrySize(entry);
            if (entrySize <= buffer.remaining()) {
                writeEntryInBuffer(entry);
            } else {
                int written = writeInSocket(socket);
                writeEntryInBuffer(entry);
                return written;
            }
        }
        if (!entriesIterator.hasNext()) {
            byte[] finalBytes = FINAL_BYTES.getBytes(StandardCharsets.UTF_8);
            if (finalBytes.length <= buffer.remaining()) {
                buffer.put(finalBytes, 0,  finalBytes.length);
            } else {
                int written = writeInSocket(socket);
                isFinalBytesShouldBeWritten = true;
                return written;
            }
        }
        return writeInSocket(socket);
    }

    private void writeEntryInBuffer(BaseEntry<byte[], Long> entry) {
        byte[] crlfBytes = CRLF.getBytes(StandardCharsets.UTF_8);
        int entrySize = getEntrySize(entry);
        buffer.put(Integer.toHexString(entrySize).getBytes(StandardCharsets.UTF_8));
        buffer.put(crlfBytes);
        buffer.put(entry.key());
        buffer.put(DELIMITER.getBytes(StandardCharsets.UTF_8));
        buffer.put(entry.value());
        buffer.put(crlfBytes);
    }

    private int getEntrySize(BaseEntry<byte[], Long> entry) {
        int size = entry.key().length;
        size += DELIMITER.getBytes(StandardCharsets.UTF_8).length;
        size += entry.value().length;
        return size;
    }

    private int writeInSocket(Socket socket) throws IOException {
        buffer.flip();
        int written = socket.write(buffer.array(), 0, buffer.limit());
        buffer.clear();
        return written;
    }
}