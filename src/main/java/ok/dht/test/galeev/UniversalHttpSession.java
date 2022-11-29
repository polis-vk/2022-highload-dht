package ok.dht.test.galeev;

import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Response;
import one.nio.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

class UniversalHttpSession extends HttpSession {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoService.class);

    private final ExecutorService executorService;

    public UniversalHttpSession(Socket socket, ExecutorService executorService, HttpServer server) {
        super(socket, server);
        this.executorService = executorService;
    }

    @Override
    protected void writeResponse(Response response, boolean includeBody) throws IOException {
        if (response instanceof ChunkedResponse) {
            // Writing headers
            super.writeResponse(response, false);
            // Writing body
            Iterator<ByteBuffer> iterator = ((ChunkedResponse) response).iterator;
            BlockingQueue<ChunkedQueueItem> daoQueue = new ArrayBlockingQueue<>(2);
            Future<?> future = executorService.submit(() -> {
                while (iterator.hasNext()) {
                    try {
                        daoQueue.put(new ChunkedQueueItem(iterator.next()));
                    } catch (InterruptedException e) {
                        LOGGER.error("Got interrupted exception while waiting write to socket");
                        Thread.currentThread().interrupt();
                    }
                }
            });
            while (!future.isDone() || !daoQueue.isEmpty()) {
                try {
                    super.write(daoQueue.take());
                } catch (InterruptedException e) {
                    LOGGER.error("Got interrupted exception while waiting read from dao");
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            super.writeResponse(response, includeBody);
        }
    }

    private static class ChunkedQueueItem extends QueueItem {
        private final ByteBuffer buffer;

        public ChunkedQueueItem(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public int remaining() {
            return buffer.remaining();
        }

        @Override
        public int write(Socket socket) throws IOException {
            return socket.write(buffer);
        }
    }
}
