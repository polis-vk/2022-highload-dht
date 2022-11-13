package ok.dht.test.galeev;

import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Response;
import one.nio.net.Socket;

import java.io.IOException;
import java.nio.ByteBuffer;

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
            super.write(new ChunkedQueueItem((ChunkedResponse) response));
        } else {
            super.writeResponse(response, includeBody);
        }
    }

    private static class ChunkedQueueItem extends QueueItem {
        private final ChunkedResponse response;
        private final ByteBuffer buffer;

        public ChunkedQueueItem(ChunkedResponse response) {
            this.response = response;
            buffer = response.iterator.next();
        }

        @Override
        public int remaining() {
            return buffer.remaining();
        }

        @Override
        public int write(Socket socket) throws IOException {
            int bytes = socket.write(buffer);
            // Lazy linked list increasing
            if (buffer.remaining() == 0 && response.iterator.hasNext()) {
                append(new ChunkedQueueItem(response));
            }
            return bytes;
        }
    }
}
