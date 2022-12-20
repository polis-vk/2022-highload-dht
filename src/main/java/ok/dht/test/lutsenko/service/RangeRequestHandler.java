package ok.dht.test.lutsenko.service;

import com.google.common.primitives.Bytes;
import ok.dht.test.lutsenko.dao.common.BaseEntry;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Iterator;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class RangeRequestHandler {

    private static final String CRLF = "\r\n";
    private static final String LAST_CHUNK = "0\r\n\r\n";
    private static final byte[] CRLF_BYTES = CRLF.getBytes(UTF_8);
    private static final byte[] LAST_CHUNK_BYTES = LAST_CHUNK.getBytes(UTF_8);
    private static final byte[] ERROR_CHUNK = buildErrorChunk();

    private static final Logger LOG = LoggerFactory.getLogger(RangeRequestHandler.class);

    private RangeRequestHandler() {
    }

    public static void handle(ExtendedSession session, Iterator<BaseEntry<String>> entriesIterator) {
        Response response = new Response(Response.OK);
        response.addHeader("Content-Type: text/plain");
        response.addHeader("Transfer-Encoding: chunked");
        response.addHeader("Connection: keep-alive");
        Session.QueueItem responseQueueItem = entriesIterator.hasNext()
                ? new RangeChunkedQueueItem(response, entriesIterator)
                : new EmptyChunkedQueueItem(response);
        sendQueueItem(session, responseQueueItem);
    }

    private static void sendQueueItem(ExtendedSession session, Session.QueueItem queueItem) {
        try {
            session.sendQueueItem(queueItem);
        } catch (Exception e) {
            try {
                session.write(ERROR_CHUNK, 0, ERROR_CHUNK.length);
            } catch (Exception e1) {
                LOG.error("Failed send ERROR_CHUNK", e1);
                ServiceUtils.closeSession(session);
            }
        }
    }

    private static byte[] buildErrorChunk() {
        byte[] message = Response.SERVICE_UNAVAILABLE.getBytes(UTF_8);
        return Bytes.concat(
                CRLF_BYTES,
                Integer.toHexString(message.length + CRLF.length() + LAST_CHUNK_BYTES.length).getBytes(UTF_8),
                message,
                CRLF_BYTES,
                LAST_CHUNK_BYTES
        );
    }

    private static class EmptyChunkedQueueItem extends Session.QueueItem {
        private final Response response;

        public EmptyChunkedQueueItem(Response response) {
            this.response = response;
        }

        @Override
        public int write(Socket socket) throws IOException {
            return socket.write(ByteBuffer.wrap(
                    Bytes.concat(response.toBytes(false), LAST_CHUNK_BYTES)
            ));
        }
    }

    private static class RangeChunkedQueueItem extends Session.QueueItem {
        private static final int WRITE_BUFFER_LIMIT = 1024;
        private static final int WRITE_BUFFER_CAPACITY = WRITE_BUFFER_LIMIT + LAST_CHUNK_BYTES.length;
        private final Iterator<BaseEntry<String>> entriesIterator;
        private ByteBuffer byteBuffer = ByteBuffer.allocate(WRITE_BUFFER_CAPACITY);
        private volatile EntryChunk peekedEntryChunk;

        private RangeChunkedQueueItem(Response response, Iterator<BaseEntry<String>> entriesIterator) {
            this.entriesIterator = entriesIterator;
            this.byteBuffer.limit(WRITE_BUFFER_LIMIT);
            this.byteBuffer.put(response.toBytes(false));
        }

        @Override
        public int remaining() {
            return byteBuffer.position();
        }

        @Override
        public int write(Socket socket) throws IOException {
            if (peekedEntryChunk != null) {
                return writeAgain(socket);
            }
            while (entriesIterator.hasNext()) {
                BaseEntry<String> entry = entriesIterator.next();
                EntryChunk entryChunk = new EntryChunk(entry);
                if (byteBuffer.remaining() < entryChunk.sizeForBuffer) {
                    byteBuffer.flip();
                    final int written = socket.write(byteBuffer);
                    if (byteBuffer.position() == byteBuffer.limit()) {
                        cleansingPut(entryChunk);
                    } else {
                        peekedEntryChunk = entryChunk;
                    }
                    return written;
                }
                entryChunk.putInBuffer(byteBuffer);
            }
            byteBuffer.limit(byteBuffer.capacity());
            byteBuffer.put(LAST_CHUNK_BYTES);
            byteBuffer.flip();
            int written = socket.write(byteBuffer);
            byteBuffer.clear();
            return written;
        }

        private int writeAgain(Socket socket) throws IOException {
            byteBuffer.flip();
            int written = socket.write(byteBuffer);
            if (byteBuffer.position() == byteBuffer.limit()) {
                cleansingPut(peekedEntryChunk);
                peekedEntryChunk = null;
            }
            return written;
        }

        // Put even entryChunk sizeInBuffer more than buffer size, so bigger buffer allocated, else just rewind current
        private void cleansingPut(EntryChunk entryChunk) {
            if (entryChunk.sizeForBuffer > byteBuffer.limit()) {
                byteBuffer = ByteBuffer.allocate(entryChunk.sizeForBuffer + LAST_CHUNK_BYTES.length);
                byteBuffer.limit(entryChunk.sizeForBuffer);
            } else {
                byteBuffer.rewind();
                byteBuffer.limit(WRITE_BUFFER_LIMIT);
            }
            entryChunk.putInBuffer(byteBuffer);
        }
    }

    private static class EntryChunk {
        private static final String ENTRY_DELIMITER = "\n";
        private static final byte[] ENTRY_DELIMITER_BYTES = ENTRY_DELIMITER.getBytes(UTF_8);
        private final byte[] key;
        private final byte[] value;
        private final byte[] sizeBytes;
        private final int sizeForBuffer;

        private EntryChunk(BaseEntry<String> entry) {
            this.key = entry.key().getBytes(UTF_8);
            this.value = Base64.getDecoder().decode(entry.value());
            int size = key.length + value.length + ENTRY_DELIMITER.length();
            this.sizeBytes = Integer.toHexString(size).getBytes(UTF_8);
            this.sizeForBuffer = sizeBytes.length + size + CRLF_BYTES.length + CRLF_BYTES.length;
        }

        public void putInBuffer(ByteBuffer byteBuffer) {
            byteBuffer.put(sizeBytes);
            byteBuffer.put(CRLF_BYTES);
            byteBuffer.put(key);
            byteBuffer.put(ENTRY_DELIMITER_BYTES);
            byteBuffer.put(value);
            byteBuffer.put(CRLF_BYTES);
        }
    }
}
