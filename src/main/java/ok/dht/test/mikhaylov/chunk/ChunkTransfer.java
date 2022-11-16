package ok.dht.test.mikhaylov.chunk;

import one.nio.http.HttpSession;
import one.nio.http.Response;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class ChunkTransfer implements AutoCloseable {
    private static final int CHUNK_SIZE = 1024; // todo: tune

    private final HttpSession session;

    private final ByteBuffer buffer;

    private boolean firstChunk = true;

    public ChunkTransfer(HttpSession session) {
        this.session = session;
        buffer = ByteBuffer.allocate(CHUNK_SIZE);
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
        byte[] body = new byte[buffer.position()];
        buffer.flip();
        buffer.get(body);
        Response response = new Response(Response.OK, body);
        if (firstChunk) {
            response.getHeaders()[1] = "Transfer-Encoding: chunked";
            firstChunk = false;
        }
        session.sendResponse(response);
        buffer.clear();
        firstChunk = false;
    }

    public void send(byte[] key, byte[] value) throws IOException {
        encode(key, value);
    }

    public void close() throws IOException {
        checkFlush(1 + 2 + 2);
        buffer.put((byte) '0');
        addCRLF();
        addCRLF();
        flush();
    }
}
