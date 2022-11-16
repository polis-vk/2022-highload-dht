package ok.dht.test.mikhaylov.chunk;

import one.nio.http.HttpSession;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.net.Socket;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class ChunkedResponse extends Response {
    private final ChunkedIterator iterator;

    public ChunkedResponse(Iterator<byte[]> iterator) {
        super(OK);
        this.iterator = new ChunkedIterator(iterator);
        super.addHeader("Transfer-Encoding: chunked");
    }

    public void writeBody(HttpSession session) throws IOException {
        while (iterator.hasNext()) {
            session.write(new ByteArrayQueueItem(iterator.next()));
        }
    }

    private static class ByteArrayQueueItem extends Session.QueueItem {
        private final byte[] data;

        public ByteArrayQueueItem(byte[] data) {
            this.data = data;
        }

        @Override
        public int write(Socket socket) throws IOException {
            return socket.write(data, 0, data.length);
        }
    }

    private static class ChunkedIterator implements Iterator<byte[]> {
        private final Iterator<byte[]> wrappedIterator;

        private boolean hasNext = true;

        private ChunkedIterator(Iterator<byte[]> iterator) {
            this.wrappedIterator = iterator;
        }

        @Override
        public boolean hasNext() {
            return hasNext;
        }

        @Override
        public byte[] next() {
            if (wrappedIterator.hasNext()) {
                byte[] next = wrappedIterator.next();
                byte[] length = Integer.toHexString(next.length).getBytes(StandardCharsets.UTF_8);
                byte[] result = new byte[length.length + 2 + next.length + 2];
                System.arraycopy(length, 0, result, 0, length.length);
                result[length.length] = '\r';
                result[length.length + 1] = '\n';
                System.arraycopy(next, 0, result, length.length + 2, next.length);
                result[length.length + 2 + next.length] = '\r';
                result[length.length + 2 + next.length + 1] = '\n';
                return result;
            } else {
                hasNext = false;
                return new byte[]{'0', '\r', '\n', '\r', '\n'};
            }
        }
    }
}
