package ok.dht.test.kazakov.service.http;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.kazakov.dao.Entry;
import ok.dht.test.kazakov.dao.TombstoneFilteringIterator;
import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Socket;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Iterator;

public class DaoHttpSession extends HttpSession {

    public DaoHttpSession(final Socket socket,
                          final HttpServer server) {
        super(socket, server);
    }

    /**
     * Adapted copy-paste of {@link HttpSession#sendResponse}.
     */
    public synchronized void sendChunkedResponse(final String responseCode,
                                                 final Iterator<Entry<MemorySegment>> response) throws IOException {
        final Response oneNioResponse = new Response(responseCode);

        Request handling = this.handling;
        if (handling == null) {
            throw new IOException("Out of order response");
        }

        server.incRequestsProcessed();

        final String connection = handling.getHeader("Connection:");
        final boolean keepAlive = handling.isHttp11()
                ? !"close".equalsIgnoreCase(connection)
                : "Keep-Alive".equalsIgnoreCase(connection);
        oneNioResponse.addHeader(keepAlive ? "Connection: Keep-Alive" : "Connection: close");
        oneNioResponse.addHeader("Transfer-Encoding: chunked");

        writeResponse(oneNioResponse, false);
        write(new ChunkedQueueItem(new TombstoneFilteringIterator<>(response)));

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

    /**
     * Not thread-safe.
     * It's ok, because one-nio locks session when writing from queue item.
     */
    private static class ChunkedQueueItem extends QueueItem {

        public static final byte ZERO_BYTE = '0';
        public static final byte TEN_BYTE = 'A';
        public static final byte SLASH_N_BYTE = '\n';
        public static final byte SLASH_R_BYTE = '\r';

        public static final int BUFFER_SIZE = 1024 * 1024;
        public static final int REMAINDER_QUEUE_SIZE = 32;

        private final TombstoneFilteringIterator<MemorySegment> data;
        private final byte[] buffer;

        // Should be implemented with byte type inlined.
        // (because Byte wrapper needs allocations to maintain)
        private final ArrayDeque<Byte> remainderQueue;

        private boolean hasWrittenKey;
        private long memorySegmentOffset;

        private ChunkedQueueItem(final TombstoneFilteringIterator<MemorySegment> data) {
            this.data = data;
            this.buffer = new byte[BUFFER_SIZE];

            this.remainderQueue = new ArrayDeque<>(REMAINDER_QUEUE_SIZE);

            this.hasWrittenKey = false;
            this.memorySegmentOffset = 0;

            lazyWriteEntryMetadata(data.peek());
        }

        @Override
        public int remaining() {
            return data.hasNext() ? 1 : 0;
        }

        private void lazyWriteLf() {
            remainderQueue.addLast(SLASH_N_BYTE);
        }

        private void lazyWriteCrlf() {
            remainderQueue.addLast(SLASH_R_BYTE);
            remainderQueue.addLast(SLASH_N_BYTE);
        }

        private void lazyWriteHexLong(final long x) {
            if (x == 0) {
                remainderQueue.addLast(ZERO_BYTE);
                return;
            }

            for (int i = Long.SIZE - Long.numberOfLeadingZeros(x) / 4 * 4 - 4; i >= 0; i -= 4) {
                final long currentDigit = (x >>> i) & 0xF;
                if (currentDigit % 16 < 10) {
                    remainderQueue.addLast((byte) (ZERO_BYTE + currentDigit % 16));
                } else {
                    remainderQueue.addLast((byte) (TEN_BYTE + currentDigit % 16 - 10));
                }
            }
        }

        private void lazyWriteEntryMetadata(@Nullable final Entry<MemorySegment> nextEntry) {
            final long entrySize = nextEntry == null
                    ? 0
                    : nextEntry.getKey().byteSize() + nextEntry.getValue().byteSize() + 1;

            lazyWriteHexLong(entrySize);
            lazyWriteCrlf();

            if (nextEntry == null) {
                lazyWriteCrlf();
            }
        }

        private boolean hasNextByte() {
            return !remainderQueue.isEmpty() || data.hasNext();
        }

        private byte nextByte() {
            if (!remainderQueue.isEmpty()) {
                return remainderQueue.removeFirst();
            }

            if (!data.hasNext()) {
                throw new IllegalStateException("Trying to get next byte on empty data");
            }

            final MemorySegment memorySegment = hasWrittenKey
                    ? data.peek().getValue()
                    : data.peek().getKey();

            if (memorySegmentOffset < memorySegment.byteSize()) {
                return MemoryAccess.getByteAtOffset(memorySegment, memorySegmentOffset++);
            }

            if (hasWrittenKey) {
                lazyWriteCrlf();
                data.next();
                lazyWriteEntryMetadata(data.peek());
            } else {
                lazyWriteLf();
            }

            memorySegmentOffset = 0;
            hasWrittenKey = !hasWrittenKey;
            return remainderQueue.pop();
        }

        @Override
        public int write(final Socket socket) throws IOException {
            int bytesWritten = 0;
            while (bytesWritten < BUFFER_SIZE && hasNextByte()) {
                buffer[bytesWritten++] = nextByte();
            }

            return socket.write(buffer, 0, bytesWritten);
        }
    }
}
