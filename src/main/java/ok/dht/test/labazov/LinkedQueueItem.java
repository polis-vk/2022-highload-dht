package ok.dht.test.labazov;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.labazov.dao.Entry;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.util.ByteArrayBuilder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class LinkedQueueItem extends Session.QueueItem {
    private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] EOF = "0\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

    private final Iterator<Entry<MemorySegment>> nextIterator;
    private final byte[] data;
    private int written;

    public LinkedQueueItem(Iterator<Entry<MemorySegment>> iterator) {
        this.written = 0;
        final ByteArrayBuilder builder = new ByteArrayBuilder();
        while (iterator.hasNext()) {
            final Entry<MemorySegment> entry = iterator.next();
            final ByteBuffer storedValue = ByteBuffer.wrap(entry.value().toByteArray());
            storedValue.getLong(); // skip
            final byte hasData = storedValue.get();

            if (hasData == 1) {
                byte[] realValue = new byte[storedValue.remaining()];
                storedValue.get(realValue);

                builder.appendHex(entry.key().toCharArray().length + realValue.length + 1)
                        .append(CRLF)
                        .append(String.valueOf(entry.key().toCharArray()))
                        .append('\n')
                        .append(realValue)
                        .append(CRLF);
                break;
            }
        }
        if (iterator.hasNext()) {
            this.nextIterator = iterator;
        } else {
            this.nextIterator = null;
            builder.append(EOF);
        }
        data = builder.trim();
    }

    @Override
    public int remaining() {
        return data.length - written;
    }

    @Override
    public int write(Socket socket) throws IOException {
        int bytes = socket.write(data, written, data.length - written, 0);
        if (bytes > 0) {
            written += bytes;
        }
        if (written == data.length && nextIterator != null) {
            append(new LinkedQueueItem(nextIterator));
        }
        return bytes;
    }
}
