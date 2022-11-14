package ok.dht.test.monakhov;

import com.google.common.primitives.Bytes;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.util.Utf8;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksIterator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ChunkedQueueItem extends Session.QueueItem {
    private final RocksIterator iterator;
    private Session.ArrayQueueItem arrayChunk;


    public ChunkedQueueItem(RocksIterator iterator) {
        this.iterator = iterator;
    }

    @Override
    public void release() {
        iterator.close();
    }

    @Override
    public int remaining() {
        return iterator.isValid() || (arrayChunk != null && arrayChunk.remaining() > 1) ? 1 : 0;
    }

    @Override
    public int write(Socket socket) throws IOException {
        if (arrayChunk == null || arrayChunk.remaining() == 0) {
            if (iterator.isValid()) {
                iterator.next();

                byte[] value = Bytes.concat(iterator.key(), "\n".getBytes(StandardCharsets.UTF_8), iterator.value());
                arrayChunk = new Session.ArrayQueueItem(value, 0, value.length, 0);
            } else {
                return 0;
            }
        }
        return arrayChunk.write(socket);
    }
}
