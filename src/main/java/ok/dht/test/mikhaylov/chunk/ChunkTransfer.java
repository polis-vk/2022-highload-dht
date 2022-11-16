package ok.dht.test.mikhaylov.chunk;

import one.nio.http.HttpSession;
import one.nio.http.Response;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class ChunkTransfer implements AutoCloseable {
    private static final int BUFFER_SIZE = 1024; // todo: tune

    private final HttpSession session;

    private final ByteBuffer buffer;

    public ChunkTransfer(HttpSession session) {
        this.session = session;
        buffer = ByteBuffer.allocate(BUFFER_SIZE);
    }

    private void addCRLF() {
        buffer.put((byte) '\r');
        buffer.put((byte) '\n');
    }

    private void encode(byte[] key, byte[] value) throws IOException {
        int bodyLength = key.length + 1 + value.length;
        byte[] length = Integer.toHexString(bodyLength).getBytes(StandardCharsets.UTF_8);
        checkFlush(length.length + 2 + bodyLength + 2);
        buffer.put(length);
        addCRLF();
        buffer.put(key);
        buffer.put((byte) '\n');
        buffer.put(value);
        addCRLF();
    }

    private void checkFlush(int size) throws IOException {
        if (buffer.remaining() < size) {
            flush();
        }
    }

    private void flush() throws IOException {
        if (buffer.position() == 0) {
            return;
        }
        byte[] body = new byte[buffer.position()];
        buffer.flip();
        buffer.get(body);
        Response response = new Response(Response.OK);
        response.addHeader("Transfer-Encoding: chunked");
        response.setBody(body);
        session.sendResponse(response);
        buffer.clear();
    }

    public void send(byte[] key, byte[] value) throws IOException {
        encode(key, value);
    }

    public void close() throws IOException {
        flush();
        buffer.put((byte) '0');
        addCRLF();
        addCRLF();
        flush();
    }
}
