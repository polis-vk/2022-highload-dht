package ok.dht.test.anikina.streaming;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.anikina.dao.Entry;
import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.net.Socket;

import java.io.IOException;
import java.util.Iterator;

public class StreamingHttpSession extends HttpSession {
    public StreamingHttpSession(Socket socket, HttpServer server) {
        super(socket, server);
    }

    public void sendResponse(Iterator<Entry<MemorySegment>> iterator) throws IOException {
        ChunkedResponse response = new ChunkedResponse();
        Request handling = this.handling;
        if (handling == null) {
            throw new IOException("Out of order response");
        }

        server.incRequestsProcessed();

        String connection = handling.getHeader("Connection:");
        boolean keepAlive = handling.isHttp11()
                ? !"close".equalsIgnoreCase(connection)
                : "Keep-Alive".equalsIgnoreCase(connection);
        response.addHeader(keepAlive ? "Connection: Keep-Alive" : "Connection: close");

        write(new StreamingQueueItem(iterator, response.toBytes(false)));

        if (!keepAlive) scheduleClose();

        handling = pipeline.pollFirst();
        this.handling = handling;
        if (handling != null) {
            if (handling == FIN) {
                scheduleClose();
            } else {
                server.handleRequest(handling, this);
            }
        }
    }
}
