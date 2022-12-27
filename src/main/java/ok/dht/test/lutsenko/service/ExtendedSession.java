package ok.dht.test.lutsenko.service;

import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.net.Socket;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExtendedSession extends HttpSession {

    public ExtendedSession(Socket socket, HttpServer server) {
        super(socket, server);
    }

    public static ExtendedSession of(HttpSession session) {
        return (ExtendedSession) session;
    }

    public static ExecutorService executor = Executors.newFixedThreadPool(4,
            r -> new Thread(r, "ExtendedSessionThread"));

    public void sendQueueItem(QueueItem queueItem) {
        executor.execute(() -> {
            try {
                Request handling = this.handling;
                if (handling == null) {
                    throw new IOException("Out of order response");
                }
                super.server.incRequestsProcessed();
                write(queueItem);
                this.handling = pipeline.pollFirst();
            } catch (Exception e) {
                ServiceUtils.closeSession(this);
            }
        });

    }
}
