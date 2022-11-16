package ok.dht.test.shik.streaming;

import java.io.IOException;

import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Response;
import one.nio.net.Socket;

public class StreamingSession extends HttpSession {

    public StreamingSession(Socket socket, HttpServer server) {
        super(socket, server);
    }

    @Override
    protected void writeResponse(Response response, boolean includeBody) throws IOException {
        if (response instanceof ChunkedResponse chunkedResponse) {
            super.writeResponse(response, false);
            super.write(new StreamingQueueItem(chunkedResponse.getIterator(), chunkedResponse.getUpperBound()));
        } else {
            super.writeResponse(response, includeBody);
        }
    }

}
