package ok.dht.test.shakhov.http.stream;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.shakhov.dao.Entry;
import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Response;
import one.nio.net.Socket;

import java.io.IOException;
import java.util.Iterator;

public class StreamAwareSession extends HttpSession {
    public StreamAwareSession(Socket socket, HttpServer server) {
        super(socket, server);
    }

    @Override
    protected void writeResponse(Response response, boolean includeBody) throws IOException {
        if (response instanceof StreamResponse streamResponse) {
            byte[] streamResponseBytes = streamResponse.toBytes(false);
            Iterator<Entry<MemorySegment>> streamIterator = streamResponse.getStreamIterator();
            StreamQueueItem streamQueueItem = new StreamQueueItem(streamResponseBytes, streamIterator);
            super.write(streamQueueItem);
        } else {
            super.writeResponse(response, includeBody);
        }
    }
}
