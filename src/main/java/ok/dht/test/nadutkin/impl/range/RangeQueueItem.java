package ok.dht.test.nadutkin.impl.range;

import one.nio.net.Session.QueueItem;
import one.nio.net.Socket;

import java.io.IOException;

public class RangeQueueItem extends QueueItem {
    private final byte[] body;
    private int offset;
    private int count;

    public RangeQueueItem(byte[] body) {
        this.body = body;
        this.offset = 0;
        this.count = body.length;
    }

    @Override
    public int write(Socket socket) throws IOException {
        int written = socket.write(body, offset, count);
        offset += written;
        count -= written;
        return written;
    }

    @Override
    public int remaining() {
        return count;
    }
}
