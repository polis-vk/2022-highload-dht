package ok.dht.test.lutsenko.service;

import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.net.Socket;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExtendedSession extends HttpSession {

    public static ExecutorService executor = Executors.newFixedThreadPool(4,
            r -> new Thread(r, "ExtendedSessionThread"));

    public ExtendedSession(Socket socket, HttpServer server) {
        super(socket, server);
    }

    public static ExtendedSession of(HttpSession session) {
        return (ExtendedSession) session;
    }

    public void sendQueueItem(QueueItem queueItem) throws IOException {
        write(queueItem);
        super.server.incRequestsProcessed();
        this.handling = pipeline.pollFirst();
    }

    @Override
    protected void processWrite() throws Exception {
        if (eventsToListen == READABLE || eventsToListen == (SSL | WRITEABLE)) {
            throw new IOException("Illegal subscription state: " + eventsToListen);
        }
        for (QueueItem item = queueHead; item != null; queueHead = item = item.next()) {
            if (isRangeItem(item)) {
                writeItemAsync(item);
            } else {
                writeItem(item);
            }
        }
        if (closing) {
            close();
        } else {
            listen(READABLE);
        }
    }

    private static boolean isRangeItem(QueueItem item) {
        return item instanceof RangeRequestHandler.RangeChunkedQueueItem
                || item instanceof RangeRequestHandler.EmptyChunkedQueueItem;
    }

    private void writeItem(QueueItem finalItem) throws IOException {
        int written = finalItem.write(socket);
        if (finalItem.remaining() > 0) {
            listen(written >= 0 ? WRITEABLE : SSL | READABLE);
            return;
        }
        finalItem.release();
    }

    private void writeItemAsync(QueueItem finalItem) {
        executor.execute(() -> {
            try {
                writeItem(finalItem);
            } catch (IOException e) {
                ServiceUtils.closeSession(this);
            }
        });
    }
}
