package ok.dht.test.shik.streaming;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import javax.annotation.Nullable;

import org.iq80.leveldb.DBIterator;

import ok.dht.test.shik.serialization.ByteArraySerializer;
import ok.dht.test.shik.serialization.ByteArraySerializerFactory;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.util.ByteArrayBuilder;

public class StreamingQueueItem extends Session.QueueItem {

    private static final String CRLF = "\r\n";

    private final DBIterator iterator;

    @Nullable
    private final byte[] upperBound;

    private final ByteArraySerializer serializer;

    private byte[] nextBody;
    private int offset;
    private boolean lastChunk;
    private boolean foundLastEntry;

    public StreamingQueueItem(DBIterator iterator, @Nullable byte[] upperBound) {
        this.iterator = iterator;
        this.upperBound = upperBound;
        serializer = ByteArraySerializerFactory.latest();
        lastChunk = false;
        foundLastEntry = false;
        readNext();
    }

    @Override
    public int write(Socket socket) throws IOException {
        int written = socket.write(nextBody, offset, nextBody.length - offset);
        offset += written;
        return written;
    }

    private Chunk readNextChunk() {
        Chunk chunk = new Chunk();
        while (!foundLastEntry && iterator.hasNext()) {
            Map.Entry<byte[], byte[]> entry = iterator.next();
            if (Arrays.equals(entry.getValue(), upperBound)) {
                foundLastEntry = true;
                break;
            }

            byte[] value = serializer.deserialize(entry.getValue()).getValue();
            if (value == null) {
                // tombstone
                continue;
            }

            if (!chunk.add(entry.getKey(), value)) {
                break;
            }
        }
        return chunk;
    }

    private void readNext() {
        Chunk chunk = readNextChunk();
        if (!chunk.isEmpty()) {
            nextBody = buildBody(chunk.getBytes());
            offset = 0;
            return;
        }

        if (!lastChunk) {
            nextBody = buildBody(new byte[0]);
            lastChunk = true;
            offset = 0;
            return;
        }

        nextBody = null;
        offset = 0;
    }

    @Override
    public int remaining() {
        if (offset == nextBody.length) {
            readNext();
        }
        return nextBody != null ? 1 : 0;
    }

    private static byte[] buildBody(byte[] nextChunk) {
        String bodyLength = Integer.toHexString(nextChunk.length);
        return new ByteArrayBuilder(bodyLength.length() + nextChunk.length + 2 * CRLF.length())
            .append(bodyLength).append(CRLF)
            .append(nextChunk).append(CRLF)
            .toBytes();
    }
}
