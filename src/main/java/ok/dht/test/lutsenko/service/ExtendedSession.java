package ok.dht.test.lutsenko.service;

import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.net.Socket;

import java.io.IOException;

public class ExtendedSession extends HttpSession {

    public ExtendedSession(Socket socket, HttpServer server) {
        super(socket, server);
    }

    public static ExtendedSession of(HttpSession session) {
        return (ExtendedSession) session;
    }

    public void sendQueueItem(QueueItem queueItem) throws IOException {
        Request handling = this.handling;
        if (handling == null) {
            throw new IOException("Out of order response");
        }
        server.incRequestsProcessed();
        write(queueItem);
        this.handling = handling = pipeline.pollFirst();
        if (handling != null) {
            if (handling == FIN) {
                scheduleClose();
            } else {
                server.handleRequest(handling, this);
            }
        }
    }
}
