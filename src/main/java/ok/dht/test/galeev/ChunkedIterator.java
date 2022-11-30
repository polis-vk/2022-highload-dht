package ok.dht.test.galeev;

import ok.dht.test.galeev.dao.entry.Entry;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Iterator;

public class ChunkedIterator implements Iterator<ByteBuffer> {
    private static final ByteBuffer END
            = ByteBuffer.wrap("0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
    private static final ByteBuffer LINE_DELIMITER
            = ByteBuffer.wrap("\n".getBytes(StandardCharsets.UTF_8));
    private static final ByteBuffer CHUNK_DELIMITER
            = ByteBuffer.wrap("\r\n".getBytes(StandardCharsets.UTF_8));
    private static final int DELIMITERS_LENGTH = 2 * CHUNK_DELIMITER.limit() + LINE_DELIMITER.limit();

    private final Iterator<Entry<String, Entry<Timestamp, byte[]>>> delegate;
    private boolean hasEnded;

    public ChunkedIterator(Iterator<Entry<String, Entry<Timestamp, byte[]>>> delegate) {
        this.delegate = delegate;
        this.hasEnded = false;
    }

    @Override
    public boolean hasNext() {
        return !hasEnded;
    }

    @Override
    public ByteBuffer next() {
        ByteBuffer buffer;
        if (delegate.hasNext()) {
            Entry<String, Entry<Timestamp, byte[]>> next = delegate.next();
            byte[] hexBodyLength = Integer.toHexString(next.key().length()
                            + LINE_DELIMITER.position(0).limit()
                            + next.value().value().length)
                    .getBytes(StandardCharsets.UTF_8);
            int bufferSize = DELIMITERS_LENGTH
                    + hexBodyLength.length
                    + next.key().length()
                    + next.value().value().length;
            buffer = ByteBuffer.allocate(bufferSize);
            buffer.put(hexBodyLength);
            buffer.put(CHUNK_DELIMITER.position(0));
            buffer.put(next.key().getBytes(StandardCharsets.UTF_8));
            buffer.put(LINE_DELIMITER.position(0));
            buffer.put(next.value().value());
            buffer.put(CHUNK_DELIMITER.position(0));
            buffer.position(0);
        } else {
            hasEnded = true;
            buffer = END.position(0);
        }
        return buffer;
    }
}
