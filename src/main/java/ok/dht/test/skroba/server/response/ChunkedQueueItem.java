package ok.dht.test.skroba.server.response;

import com.google.common.primitives.Bytes;
import ok.dht.test.skroba.db.base.Entity;
import one.nio.net.Session;
import one.nio.net.Socket;
import org.iq80.leveldb.DBIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

public class ChunkedQueueItem extends Session.QueueItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkedQueueItem.class);
    private static final byte[] ENDING_CHUNK = new byte[]{ '0', '\r', '\n', '\r', '\n' };
    private static final byte[] CRLF_BYTES = new byte[]{ '\r', '\n' };
    private static final byte[] DELIMITER_BYTES = new byte[]{ '\n' };
    private final DBIterator iterator;
    private final byte[] end;
    private Session.ArrayQueueItem arrayChunk;
    private boolean finished;
    
    public ChunkedQueueItem(ChunkedResponse response) {
        super();
        byte[] headers = response.toBytes(false);
        
        this.arrayChunk = new Session.ArrayQueueItem(headers, 0, headers.length, 0);
        this.iterator = response.iterator;
        this.end = response.end;
    }
    
    @Override
    public int remaining() {
        return finished ? 0 : 1;
    }
    
    @Override
    public void release() {
        try {
            iterator.close();
        } catch (IOException e) {
            LOGGER.warn("Exception while closing iterator!");
        }
    }
    
    @Override
    public int write(Socket socket) throws IOException {
        if (arrayChunk.remaining() == 0) {
            if (iterator.hasNext()) {
                Map.Entry<byte[], byte[]> entry = iterator.next();
                
                if (end != null && Arrays.compare(entry.getKey(), end) >= 0) {
                    return finish(socket);
                }
                
                byte[] body = Entity.deserialize(entry.getValue())
                        .getValue();
                byte[] chunkSize = Integer.toHexString(entry.getKey().length + DELIMITER_BYTES.length + body.length)
                        .getBytes(StandardCharsets.UTF_8);
                byte[] value = Bytes.concat(chunkSize, CRLF_BYTES, entry.getKey(), DELIMITER_BYTES, body, CRLF_BYTES);
                
                arrayChunk = new Session.ArrayQueueItem(value, 0, value.length, 0);
            } else {
                return finish(socket);
            }
        }
        
        return arrayChunk.write(socket);
    }
    
    private int finish(final Socket socket) throws IOException {
        finished = true;
        return socket.write(ENDING_CHUNK, 0, ENDING_CHUNK.length, 0);
    }
}
