package ok.dht.test.kurdyukov.http;

import com.google.common.primitives.Bytes;
import ok.dht.test.kurdyukov.dao.model.DaoEntry;
import ok.dht.test.kurdyukov.utils.ObjectMapper;
import one.nio.net.Session;
import one.nio.net.Socket;
import org.iq80.leveldb.DBIterator;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.iq80.leveldb.impl.Iq80DBFactory.bytes;

public class ChunkedQueueItem extends Session.QueueItem {
    private static final byte[] ENDING_CHUNK = bytes("0\r\n\r\n");
    private static final byte[] CRLF_BYTES = bytes("\r\n");
    private static final byte[] DELIMITER_BYTES = bytes("\n");
    private final DBIterator dbIterator;
    private final byte[] upperBound;

    private boolean terminate;

    public ChunkedQueueItem(DBIterator iterator, byte[] upperBound) {
        this.dbIterator = iterator;
        this.upperBound = upperBound;
    }

    @Override
    public int remaining() {
        return terminate ? 0 : 1;
    }

    @Override
    public int write(Socket socket) throws IOException {
        if (dbIterator.hasNext()) {
            var entry = dbIterator.next();

            if (upperBound == null || Arrays.compare(entry.getKey(), upperBound) < 0) {
                DaoEntry daoEntry;
                try {
                    daoEntry = ObjectMapper.deserialize(entry.getValue());
                } catch (ClassNotFoundException e) {
                    throw new IOException(e);
                }

                if (daoEntry.isTombstone) {
                    return 0;
                }

                byte[] chunkSize = bytes(
                        Integer.toHexString(
                                entry.getKey().length
                                        + DELIMITER_BYTES.length
                                        + daoEntry.value.length
                        )
                );

                byte[] value = Bytes.concat(
                        chunkSize, CRLF_BYTES, entry.getKey(),
                        DELIMITER_BYTES, daoEntry.value, CRLF_BYTES
                );

                return socket.write(
                        ByteBuffer
                                .allocate(value.length)
                                .put(value)
                                .position(0)
                );
            } else {
                terminate = true;
                return socket.write(ENDING_CHUNK, 0, ENDING_CHUNK.length, 0);
            }
        } else {
            terminate = true;
            return socket.write(ENDING_CHUNK, 0, ENDING_CHUNK.length, 0);
        }
    }
}
