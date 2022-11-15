package ok.dht.test.shashulovskiy.chunk;

import one.nio.http.HttpSession;
import one.nio.http.Response;
import one.nio.util.ByteArrayBuilder;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ChunkedResponse extends Response {

    private static final int CHUNK_SIZE = 1024;
    public static Response START_CHUNK;
    public static Response END_CHUNK;

    static {
        START_CHUNK = new Response(Response.OK, Response.EMPTY);
        START_CHUNK.getHeaders()[1] = "Transfer-Encoding: chunked";

        END_CHUNK = new Response(Response.OK, genBytes(0, Response.EMPTY));
    }

    private final HttpSession httpSession;
    private ByteArrayBuilder byteBuffer;

    public ChunkedResponse(String resultCode, HttpSession httpSession) {
        super(resultCode, Response.EMPTY);

        this.httpSession = httpSession;
        this.byteBuffer = new ByteArrayBuilder();
    }

    @Override
    public byte[] getBody() {
        return genBytes(byteBuffer.length(), byteBuffer.toBytes());
    }

    private static byte[] genBytes(int length, byte[] data) {
        return new ByteArrayBuilder().append(Integer.toHexString(length))
                .append('\r').append('\n')
                .append(data)
                .append('\r').append('\n')
                .toBytes();
    }

    public void send(byte[] data) throws IOException {
        if (byteBuffer.length() + data.length > CHUNK_SIZE && byteBuffer.length() > 0) {
            flush();
        }
        byteBuffer.append(data);
    }

    private void flush() throws IOException {
        httpSession.sendResponse(this);
        byteBuffer = new ByteArrayBuilder();
    }

    public void startSending() throws IOException {
        httpSession.sendResponse(START_CHUNK);
    }

    public void finishSending() throws IOException {
        if (byteBuffer.length() > 0) {
            flush();
        }
        flush();
    }
}
