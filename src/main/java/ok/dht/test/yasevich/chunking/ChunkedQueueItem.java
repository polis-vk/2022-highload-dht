package ok.dht.test.yasevich.chunking;

import ok.dht.test.yasevich.dao.Entry;
import one.nio.net.Session;
import one.nio.net.Socket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class ChunkedQueueItem extends Session.QueueItem {
    private static final String CRLF = "\r\n";
    private static final byte[] ENDING_CHUNK = ('0' + CRLF + CRLF).getBytes(StandardCharsets.UTF_8);
    private static final byte[] CRLF_BYTES = CRLF.getBytes(StandardCharsets.UTF_8);
    private static final byte[] KEY_VALUE_DELIMITER_BYTES = "\n".getBytes(StandardCharsets.UTF_8);

    private final ChunkedResponse response;
    private final Iterator<Entry<byte[]>> entries;
    private volatile boolean httpHeaderSent;
    private volatile boolean endingSent;

    public ChunkedQueueItem(ChunkedResponse response) {
        this.response = response;
        this.entries = response.entries;
    }

    @Override
    public int remaining() {
        return !httpHeaderSent || entries.hasNext() || !endingSent ? 1 : 0;
    }

    @Override
    public int write(Socket socket) throws IOException {
        if (!httpHeaderSent) {
            byte[] bytes = response.toBytes(false);
            httpHeaderSent = true;
            return socket.write(bytes, 0, bytes.length);
        }
        if (entries.hasNext()) {
            return socket.write(makeChunk(entries.next()));
        }
        endingSent = true;
        return socket.write(ENDING_CHUNK, 0, ENDING_CHUNK.length);
    }

    private static ByteBuffer makeChunk(Entry<byte[]> entry) {
        int entryLength = entry.key().length + KEY_VALUE_DELIMITER_BYTES.length + entry.value().length;
        byte[] chunkContentLength = Integer.toHexString(entryLength).getBytes(StandardCharsets.UTF_8);
        ByteBuffer chunk = ByteBuffer
                .allocate(chunkContentLength.length + CRLF_BYTES.length + entryLength + CRLF_BYTES.length);
        chunk.put(chunkContentLength);
        chunk.put(CRLF_BYTES);
        chunk.put(entry.key());
        chunk.put(KEY_VALUE_DELIMITER_BYTES);
        chunk.put(entry.value());
        chunk.put(CRLF_BYTES);
        return chunk.flip();
    }
}
