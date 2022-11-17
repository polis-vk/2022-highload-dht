package ok.dht.test.shakhov.http.stream;

import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Response;
import one.nio.net.Socket;

import java.io.IOException;

public class StreamAwareSession extends HttpSession {
    public StreamAwareSession(Socket socket, HttpServer server) {
        super(socket, server);
    }

    @Override
    protected void writeResponse(Response response, boolean includeBody) throws IOException {
        if (response instanceof StreamResponse streamResponse) {
            StreamQueueItem streamQueueItem = new StreamQueueItem(streamResponse.toBytes(false), streamResponse.getStreamIterator());
            super.write(streamQueueItem);
        } else {
            super.writeResponse(response, includeBody);
        }
    }
}
