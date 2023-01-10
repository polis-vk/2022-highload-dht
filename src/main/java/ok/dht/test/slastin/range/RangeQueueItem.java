package ok.dht.test.slastin.range;

import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.util.Utf8;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static ok.dht.test.slastin.range.RangeResponse.HTTP11_HEADER;
import static ok.dht.test.slastin.range.RangeResponse.PROTOCOL_HEADER_LENGTH;

public class RangeQueueItem extends Session.QueueItem {
    private static final Logger log = LoggerFactory.getLogger(RangeQueueItem.class);
    private static final int CAPACITY = 128 * 1024;
    private static final int VALUE_SHIFT = Long.BYTES + 1;

    private final RocksIterator rangeIterator;
    private final ByteBuffer buffer;
    private boolean wasEndWritten;

    public RangeQueueItem(RangeResponse rangeResponse) {
        this.rangeIterator = rangeResponse.getRangeIterator();
        this.buffer = ByteBuffer.allocate(CAPACITY);
        wasEndWritten = false;

        putHeaders(rangeResponse.getHeaders(), rangeResponse.getHeaderCount());
    }

    private void putHeaders(String[] headers, int headerCount) {
        int estimatedSize = PROTOCOL_HEADER_LENGTH + headerCount * 2;
        for (int i = 0; i < headerCount; i++) {
            estimatedSize += headers[i].length();
        }

        if (estimatedSize > buffer.remaining()) {
            String msg = "not enough space for headers";
            log.error(msg);
            throw new RuntimeException(msg);
        }

        buffer.put(HTTP11_HEADER);
        for (int i = 0; i < headerCount; i++) {
            buffer.put(Utf8.toBytes(headers[i]));
            putCRLF();
        }
        putCRLF();
    }

    private void putEntities() {
        int position = buffer.position();
        int countEntities = 0;

        while (rangeIterator.isValid()) {
            if (putEntity()) {
                ++countEntities;
            } else if (position == 0 && countEntities == 0) {
                // size of key and value is greater than buffer size -> skips this entry
                log.error("not enough space for entity with key " + Utf8.toString(rangeIterator.key()));
            } else {
                // else try to write to socket filled buffer
                return;
            }

            rangeIterator.next();
        }

        try {
            rangeIterator.status();
        } catch (RocksDBException e) {
            log.error("rangeIterator exception while status", e);
        }

        putEnd();
    }

    private boolean putEntity() {
        byte[] key = rangeIterator.key();
        byte[] value = rangeIterator.value();

        int count = key.length + 1 + value.length - VALUE_SHIFT;
        byte[] bytesCount = countToBytes(count);

        if (bytesCount.length + count + 4 > buffer.remaining()) {
            return false;
        }

        buffer.put(bytesCount);
        putCRLF();
        buffer.put(key);
        buffer.put((byte) '\n');
        buffer.put(value, VALUE_SHIFT, value.length - VALUE_SHIFT);
        putCRLF();

        return true;
    }

    private void putEnd() {
        if (buffer.remaining() >= 5) {
            buffer.put(countToBytes(0));
            putCRLF();
            putCRLF();
            wasEndWritten = true;
        }
    }

    private void putCRLF() {
        buffer.put((byte) '\r').put((byte) '\n');
    }

    private static byte[] countToBytes(int bytesCount) {
        return Integer.toHexString(bytesCount).getBytes(StandardCharsets.US_ASCII);
    }

    @Override
    public int write(Socket socket) throws IOException {
        putEntities();

        int endPosition = buffer.position();
        int bytes = socket.write(buffer.array(), 0, endPosition);

        // shifts remaining data to the left
        buffer.position(bytes);
        buffer.limit(endPosition);
        buffer.compact();

        return bytes;
    }

    @Override
    public int remaining() {
        return wasEndWritten ? 0 : 1;
    }

    @Override
    public void release() {
        rangeIterator.close();
    }
}
