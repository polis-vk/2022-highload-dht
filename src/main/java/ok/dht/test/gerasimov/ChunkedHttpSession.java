package ok.dht.test.gerasimov;

import ok.dht.test.gerasimov.exception.ChunkedHttpSessionException;
import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Response;
import one.nio.net.Socket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

public class ChunkedHttpSession extends HttpSession {
    private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] LAST_BLOCK = "0\r\n\r\n".getBytes(StandardCharsets.UTF_8);

    private Supplier<byte[]> supplier;

    public ChunkedHttpSession(Socket socket, HttpServer server) {
        super(socket, server);
    }

    public void sendChunkedResponse(Response response, Supplier<byte[]> supplier) {
        try {
            this.supplier = supplier;
            response.addHeader("Transfer-Encoding: chunked");
            this.sendResponse(response);
            processChain();
        } catch (IOException e) {
            throw new ChunkedHttpSessionException("Failed send chunked response", e);
        }
    }

    @Override
    protected void processWrite() {
        try {
            super.processWrite();
            processChain();
        } catch (Exception e) {
            throw new ChunkedHttpSessionException("Failed process write", e);
        }
    }

    @Override
    public synchronized void scheduleClose() {
        if (supplier != null) {
            super.scheduleClose();
        }
    }


    private void processChain() {
        try {
            if (supplier != null) {
                while (queueHead == null) {
                    byte[] bytes = supplier.get();
                    if (bytes == null) {
                        write(LAST_BLOCK, 0, LAST_BLOCK.length);
                        scheduleClose();
                        return;
                    }

                    byte[] chunk = createChunk(bytes);
                    write(chunk, 0, chunk.length);
                }
            }
        } catch (IOException e) {
            throw new ChunkedHttpSessionException("Failed process chain", e);
        }
    }

    private static byte[] createChunk(byte[] data) {
        byte[] hexLength = Integer.toHexString(data.length).getBytes(StandardCharsets.UTF_8);
        int chunkLength = hexLength.length + CRLF.length + data.length + CRLF.length;

        return ByteBuffer.allocate(chunkLength)
                .put(hexLength)
                .put(CRLF)
                .put(data)
                .put(CRLF)
                .array();
    }
}