package ok.dht.test.shakhov.http.stream;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.shakhov.dao.Entry;
import one.nio.net.Session.ArrayQueueItem;
import one.nio.net.Socket;
import one.nio.util.Utf8;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class StreamQueueItem extends ArrayQueueItem {
    private static final byte[] END_CHUNK = Utf8.toBytes("0\r\n\r\n");
    private static final byte[] CRLF = Utf8.toBytes("\r\n");
    private static final byte[] LF = Utf8.toBytes("\n");

    private final Iterator<Entry<MemorySegment>> streamIterator;
    private boolean endSent = false;

    public StreamQueueItem(byte[] streamStart, Iterator<Entry<MemorySegment>> streamIterator) {
        super(streamStart, 0, streamStart.length, 0);
        this.streamIterator = streamIterator;
    }

    @Override
    public int remaining() {
        int currentDataRemaining = super.remaining();
        if (currentDataRemaining > 0) {
            return currentDataRemaining;
        } else if (streamIterator.hasNext() || !endSent) {
            return 1;
        } else {
            return 0;
        }
    }

    @Override
    public int write(Socket socket) throws IOException {
        if (super.remaining() <= 0) {
            if (streamIterator.hasNext()) {
                Entry<MemorySegment> entry = streamIterator.next();
                byte[] entryChunk = createChunk(entry);
                setData(entryChunk);
            } else if (!endSent) {
                setData(END_CHUNK);
                endSent = true;
            }
        }
        return super.write(socket);
    }

    private void setData(byte[] data) {
        this.data = data;
        this.offset = 0;
        this.count = data.length;
        this.flags = 0;
        this.written = 0;
    }

    private static byte[] createChunk(Entry<MemorySegment> entry) {
        byte[] key = entry.key().toByteArray();
        byte[] value = entry.value().toByteArray();
        int dataSize = key.length + LF.length + value.length;
        byte[] dataSizeBytes = Integer.toHexString(dataSize).getBytes(StandardCharsets.US_ASCII);
        byte[] chunk = new byte[dataSizeBytes.length + CRLF.length + dataSize + CRLF.length];

        int currentPos = 0;
        currentPos = copyArray(dataSizeBytes, chunk, currentPos);
        currentPos = copyArray(CRLF, chunk, currentPos);
        currentPos = copyArray(key, chunk, currentPos);
        currentPos = copyArray(LF, chunk, currentPos);
        currentPos = copyArray(value, chunk, currentPos);
        copyArray(CRLF, chunk, currentPos);

        return chunk;
    }

    private static int copyArray(byte[] from, byte[] to, int currentPos) {
        System.arraycopy(from, 0, to, currentPos, from.length);
        return currentPos + from.length;
    }
}
