package ok.dht.test.monakhov.chunk;

import com.google.common.primitives.Bytes;
import ok.dht.test.monakhov.model.EntryWrapper;
import one.nio.net.Session;
import one.nio.net.Socket;
import org.rocksdb.RocksIterator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static one.nio.serial.Serializer.deserialize;

public class ChunkedQueueItem extends Session.QueueItem {
    private static final String CRLF = "\r\n";
    private static final byte[] ENDING_CHUNK = ('0' + CRLF + CRLF).getBytes(StandardCharsets.UTF_8);
    private static final byte[] CRLF_BYTES = CRLF.getBytes(StandardCharsets.UTF_8);
    private static final byte[] DELIMITER_BYTES = "\n".getBytes(StandardCharsets.UTF_8);
    private final RocksIterator iterator;
    private Session.ArrayQueueItem arrayChunk;

    private boolean ended = false;


    public ChunkedQueueItem(ChunkedResponse response) {
        this.iterator = response.iterator;

        byte[] headers = response.toBytes(false);
        arrayChunk = new Session.ArrayQueueItem(headers, 0, headers.length, 0);
    }

    @Override
    public void release() {
        iterator.close();
    }

    @Override
    public int remaining() {
        return !ended ? 1 : 0;
    }

    @Override
    public int write(Socket socket) throws IOException {
        try {
            if (arrayChunk.remaining() == 0) {
                if (iterator.isValid()) {
                    EntryWrapper entry = (EntryWrapper) deserialize(iterator.value());
                    byte[] chunkSize = Integer.toHexString(
                        iterator.key().length + DELIMITER_BYTES.length + entry.bytes.length
                    ).getBytes(StandardCharsets.UTF_8);

                    byte[] value = Bytes.concat(
                        chunkSize, CRLF_BYTES, iterator.key(),
                        DELIMITER_BYTES, entry.bytes, CRLF_BYTES
                    );
                    arrayChunk = new Session.ArrayQueueItem(value, 0, value.length, 0);

                    iterator.next();
                } else {
                    ended = true;
                    return socket.write(ENDING_CHUNK, 0, ENDING_CHUNK.length, 0);
                }
            }
            return arrayChunk.write(socket);
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
    }
}
