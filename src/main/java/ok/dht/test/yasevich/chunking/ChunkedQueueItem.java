package ok.dht.test.yasevich.chunking;

import ok.dht.test.yasevich.dao.Entry;
import one.nio.net.Session;
import one.nio.net.Socket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;

public class ChunkedQueueItem extends Session.QueueItem {
    private static final String CRLF = "\n\r";
    private static final byte[] ENDING_CHUNK = ('0' + CRLF + CRLF).getBytes();
    private static final byte[] CRLF_BYTES = CRLF.getBytes();

    private final ChunkedResponse response;
    private final Iterator<Entry<byte[]>> entries;
    private volatile boolean beginningSent;
    private volatile boolean endingSent;

    public ChunkedQueueItem(ChunkedResponse response) {
        this.response = response;
        this.entries = response.entries;
    }

    @Override
    public int remaining() {
        return !beginningSent || entries.hasNext() || !endingSent ? 1 : 0;
    }

    @Override
    public int write(Socket socket) throws IOException {
        if (!beginningSent) {
            byte[] bytes = response.toBytes(false);
            beginningSent = true;
            return socket.write(bytes, 0, bytes.length);
        }
        if (!entries.hasNext()) {
            endingSent = true;
            return socket.write(ENDING_CHUNK, 0, ENDING_CHUNK.length);
        }
        byte[] bytes = makeChunk(entries.next());
        return socket.write(bytes, 0, bytes.length);
    }

    private static byte[] makeChunk(Entry<byte[]> entry) {
        int entryLength = entry.key().length + (entry.value() == null ? 0 : entry.value().length);
        byte[] chunkContentLength = Integer.toHexString(entryLength).getBytes();
        ByteBuffer chunk = ByteBuffer.allocate(chunkContentLength.length + CRLF_BYTES.length * 2 + entryLength);
        chunk.put(chunkContentLength);
        chunk.put(CRLF_BYTES);
        chunk.put(entry.key());
        if (entry.value() != null) {
            chunk.put(entry.value());
        }
        chunk.put(CRLF_BYTES);
        return chunk.array();
    }
}
