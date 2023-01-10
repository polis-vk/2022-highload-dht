package ok.dht.test.ilin.session;

import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Response;
import one.nio.net.Socket;

import java.io.IOException;
import java.util.function.Supplier;

public class ExpandableHttpSession extends HttpSession {
    private Supplier<byte[]> chunkSupplier;

    public ExpandableHttpSession(Socket socket, HttpServer server) {
        super(socket, server);
    }

    public void sendChunkedResponse(Response response, Supplier<byte[]> chunkSupplier) throws IOException {
        this.chunkSupplier = chunkSupplier;
        response.addHeader("Transfer-Encoding: chunked");
        response.setBody(chunkSupplier.get());
        sendResponse(response);
        nextChunk();
    }

    @Override
    public synchronized void scheduleClose() {
        if (chunkSupplier == null) {
            super.scheduleClose();
        }
    }

    @Override
    protected void processWrite() throws Exception {
        super.processWrite();
        nextChunk();
    }

    private void nextChunk() throws IOException {
        if (chunkSupplier != null) {
            while (queueHead == null) {
                byte[] chunk = chunkSupplier.get();
                if (chunk == null) {
                    super.scheduleClose();
                    break;
                }
                write(chunk, 0, chunk.length);
            }
        }
    }
}
