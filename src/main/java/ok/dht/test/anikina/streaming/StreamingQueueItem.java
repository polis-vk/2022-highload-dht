package ok.dht.test.anikina.streaming;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.anikina.dao.Entry;
import ok.dht.test.anikina.utils.Utils;
import one.nio.net.Session.QueueItem;
import one.nio.net.Socket;
import one.nio.util.ByteArrayBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

public class StreamingQueueItem extends QueueItem {
    private static final byte[] LINE_END = Utils.toBytes("\r\n");
    private static final byte[] INPUT_END = Utils.toBytes("0\r\n");
    private static final byte[] OUTPUT_SPLIT = Utils.toBytes("\n");
    private final Iterator<Entry<MemorySegment>> iterator;
    private final byte[] headers;
    private byte[] bytes;
    private boolean sendHeaders = true;
    private int count;
    private int written;

    public StreamingQueueItem(Iterator<Entry<MemorySegment>> iterator, byte[] headers) {
        super();
        this.iterator = iterator;
        this.headers = Arrays.copyOf(headers, headers.length);
    }

    @Override
    public int remaining() {
        if (iterator.hasNext() || written < count) {
            return 1;
        }
        return 0;
    }

    @Override
    public int write(Socket socket) throws IOException {
        boolean finalChunk = false;
        if (written == count) {
            byte[] data;
            if (iterator.hasNext()) {
                data = makeResponse(iterator.next());
            } else {
                data = makeFinalResponse();
                finalChunk = true;
            }

            if (sendHeaders) {
                bytes = new ByteArrayBuilder()
                        .append(headers)
                        .append(data)
                        .toBytes();
                sendHeaders = false;
            } else {
                bytes = data;
            }

            written = 0;
            count = bytes.length;
        }
        int bytesWritten = socket.write(bytes, written, count - written, 0);
        if (bytesWritten > 0) {
            written += bytesWritten;
        }
        if (written == count && !finalChunk) {
            write(socket);
        }
        return written;
    }

    private static byte[] makeFinalResponse() {
        return new ByteArrayBuilder()
                .append(INPUT_END)
                .append(LINE_END)
                .toBytes();
    }

    private static byte[] makeResponse(Entry<MemorySegment> entry) {
        byte[] key = Utils.toBytes(entry.key());
        byte[] value = Utils.toBytes(entry.value());
        byte[] data = new ByteArrayBuilder()
                .append(key)
                .append(OUTPUT_SPLIT)
                .append(value)
                .toBytes();

        String size = Integer.toHexString(data.length);
        return new ByteArrayBuilder()
                .append(size)
                .append(LINE_END)
                .append(data)
                .append(LINE_END)
                .toBytes();
    }
}
