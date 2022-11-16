package ok.dht.test.armenakyan.chunk;

import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Response;
import one.nio.net.Socket;

import java.io.IOException;

public class ChunkedHttpSession extends HttpSession {
    public ChunkedHttpSession(Socket socket, HttpServer server) {
        super(socket, server);
    }

    @Override
    protected void writeResponse(Response response, boolean includeBody) throws IOException {
        if (response instanceof ChunkedResponse) {
            super.writeResponse(response, false); // write headers

            write(new BufferedChunkQueueItem(((ChunkedResponse) response)));
            return;
        }

        super.writeResponse(response, includeBody);
    }
}
