package ok.dht.test.kiselyov.util;

import ok.dht.test.kiselyov.dao.BaseEntry;
import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Socket;

import java.io.IOException;
import java.util.Iterator;

public class CustomHttpSession extends HttpSession {
    public CustomHttpSession(Socket socket, HttpServer server) {
        super(socket, server);
    }

    @Override
    public synchronized void sendResponse(Response response) throws IOException {
        if (response instanceof ChunkedResponse) {
            Request handling = this.handling;
            if (handling == null) {
                throw new IOException("Out of order response");
            }

            server.incRequestsProcessed();

            Iterator<BaseEntry<byte[], Long>> entriesIterator = ((ChunkedResponse) response).getIterator();
            write(new IterableQueueItem(entriesIterator, (ChunkedResponse) response));

            if ((this.handling = handling = pipeline.pollFirst()) != null) {
                if (handling == FIN) {
                    scheduleClose();
                } else {
                    server.handleRequest(handling, this);
                }
            }
        } else {
            super.sendResponse(response);
        }

    }
}