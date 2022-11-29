package ok.dht.test.galeev;

import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Response;
import one.nio.net.Socket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;

class UniversalHttpSession extends HttpSession {
    public UniversalHttpSession(Socket socket, HttpServer server) {
        super(socket, server);
    }

    @Override
    protected void writeResponse(Response response, boolean includeBody) throws IOException {
        if (response instanceof ChunkedResponse) {
            // Writing headers
            super.writeResponse(response, false);
            // Writing body
            Iterator<ByteBuffer> iterator = ((ChunkedResponse) response).iterator;
            while (iterator.hasNext()) {
                super.write(new ChunkedQueueItem(iterator.next()));
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
