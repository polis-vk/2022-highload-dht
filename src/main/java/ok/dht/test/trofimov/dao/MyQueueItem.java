package ok.dht.test.trofimov.dao;

import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.util.Base64;
import one.nio.util.ByteArrayBuilder;
import one.nio.util.Utf8;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class MyQueueItem extends Session.ArrayQueueItem {
    private static final char DELIMETER = '\n';
    private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] EOF = "0\r\n\r\n".getBytes(StandardCharsets.UTF_8);
    private final ChunkedResponse chunkedResponse;
    private boolean lastChunkSent;
    private boolean headersSent;

    public MyQueueItem(ChunkedResponse response) {
        super(new byte[0], 0, 0, 0);
        this.chunkedResponse = response;
    }

    @Override
    public int write(Socket socket) throws IOException {
        if (headersSent) {
            if (written == count) {
                if (chunkedResponse.getData().hasNext()) {
                    data = getBytesOfData();
                    offset = 0;
                    count = data.length;
                    written = 0;
                    writeToSocket(socket);
                } else if (lastChunkSent) {
                    next = null;
                } else {
                    data = EOF;
                    offset = 0;
                    count = EOF.length;
                    written = 0;
                    writeToSocket(socket);
                    lastChunkSent = true;
                }
            } else {
                writeToSocket(socket);
            }
        } else {
            data = chunkedResponse.toBytes(false);
            offset = 0;
            count = data.length;
            written = 0;
            writeToSocket(socket);
            next = this;
            headersSent = true;
        }
        return written;
    }

    private void writeToSocket(Socket socket) throws IOException {
        int bytes = socket.write(data, written, count - written);
        if (bytes > 0) {
            written += bytes;
        }
    }

    private byte[] getBytesOfData() {
        Entry<String> entry = chunkedResponse.getData().next();
        byte[] keyBytes = Utf8.toBytes(entry.key());
        byte[] valueBytes = Base64.decodeFromChars(entry.value().toCharArray());
        int length = keyBytes.length + valueBytes.length + 1;
        ByteArrayBuilder bytesBuilder = new ByteArrayBuilder();
        bytesBuilder
                .append(Utf8.toBytes(Integer.toHexString(length)))
                .append(CRLF)
                .append(keyBytes)
                .append(DELIMETER)
                .append(valueBytes)
                .append(CRLF);
        return bytesBuilder.toBytes();
    }

    @Override
    public int remaining() {
        return count - written;
    }
}
