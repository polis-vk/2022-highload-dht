package ok.dht.test.gerasimov;

import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Response;
import one.nio.net.Socket;

import java.io.IOException;
import java.util.function.Supplier;

public class ChunkedHttpSession extends HttpSession {
    Supplier<byte[]> dataSupplier;

    public ChunkedHttpSession(Socket socket, HttpServer server) {
        super(socket, server);
    }

    public void sendResponseWithSupplier(Response response, Supplier<byte[]> supplier) throws IOException {
        this.dataSupplier = supplier;
        response.addHeader("Transfer-Encoding: chunked");
        this.sendResponse(response);
        processChain();
    }

    @Override
    protected void processWrite() throws Exception {
        super.processWrite();
        processChain();
    }

    private void processChain() throws IOException {
        if (dataSupplier != null) {
            while (queueHead == null) {
                byte[] bytes = dataSupplier.get();
                if (bytes == null) {
                    return;
                }
                write(bytes, 0, bytes.length);
            }
        }
    }
}