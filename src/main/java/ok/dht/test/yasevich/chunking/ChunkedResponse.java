package ok.dht.test.yasevich.chunking;

import ok.dht.test.yasevich.artyomdrozdov.PeekIterator;
import ok.dht.test.yasevich.dao.Entry;
import one.nio.http.Response;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.function.Supplier;

public class ChunkedResponse extends Response {
    private static final String CRLF = "\r\n";
    private static final byte[] ENDING_CHUNK = ('0' + CRLF + CRLF).getBytes(StandardCharsets.UTF_8);
    private static final byte[] CRLF_BYTES = CRLF.getBytes(StandardCharsets.UTF_8);
    private static final int DIRECT_BYTE_BUF_SIZE = 250 * 1024;
    private static final byte[] KEY_VALUE_DELIMITER_BYTES = "\n".getBytes(StandardCharsets.UTF_8);

    private final Supplier<Iterator<Entry<byte[]>>> entriesSupplier;
    private ByteBuffer buffer;
    private PeekIterator<Entry<byte[]>> entries;
    private boolean endingSent;

    public ChunkedResponse(String resultCode, Supplier<Iterator<Entry<byte[]>>> entriesSupplier) {
        super(resultCode, Response.EMPTY);
        this.entriesSupplier = entriesSupplier;
        getHeaders()[1] = "Transfer-Encoding: chunked";
    }

    private void init() { // Не хочется это делать в селекторах при создании объекта ChunkedResponse
        entries = new PeekIterator<>(entriesSupplier.get());
        buffer = ByteBuffer.allocateDirect(DIRECT_BYTE_BUF_SIZE);
        buffer.put(this.toBytes(false));
    }

    public ByteBuffer getChunks() {
        if (entries == null) {
            init();
        } else {
            buffer.clear();
        }
        while (entries.hasNext()) {
            Entry<byte[]> next = entries.peek();
            if (next.value().length > buffer.capacity()) {
                throw new IllegalStateException("Value is too big");
            }
            if (next.key().length + next.value().length > buffer.remaining()) {
                return buffer.flip();
            }
            putEntry(next, buffer);
            entries.next();
        }
        if (!endingSent && ENDING_CHUNK.length <= buffer.remaining()) {
            buffer.put(ENDING_CHUNK);
            endingSent = true;
        }
        return buffer.flip();
    }

    public boolean isDone() {
        return endingSent;
    }

    private static void putEntry(Entry<byte[]> entry, ByteBuffer buffer) {
        int entryLength = entry.key().length + KEY_VALUE_DELIMITER_BYTES.length + entry.value().length;
        byte[] chunkContentLength = Integer.toHexString(entryLength).getBytes(StandardCharsets.UTF_8);
        buffer.put(chunkContentLength);
        buffer.put(CRLF_BYTES);
        buffer.put(entry.key());
        buffer.put(KEY_VALUE_DELIMITER_BYTES);
        buffer.put(entry.value());
        buffer.put(CRLF_BYTES);
    }
}
