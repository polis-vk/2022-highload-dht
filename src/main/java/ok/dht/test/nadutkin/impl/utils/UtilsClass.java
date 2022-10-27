package ok.dht.test.nadutkin.impl.utils;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.nadutkin.database.Entry;
import one.nio.http.HttpServerConfig;
import one.nio.server.AcceptorConfig;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static ok.dht.test.nadutkin.impl.utils.Constants.LOG;

public final class UtilsClass {
    private UtilsClass() {
    }

    public interface Data extends Iterable<Entry<MemorySegment>> {
        @Override
        Iterator<Entry<MemorySegment>> iterator();
    }

    public static class TombstoneFilteringIterator implements Iterator<Entry<MemorySegment>> {
        private final Iterator<Entry<MemorySegment>> iterator;
        private Entry<MemorySegment> current;

        public TombstoneFilteringIterator(Iterator<Entry<MemorySegment>> iterator) {
            this.iterator = iterator;
        }

        public Entry<MemorySegment> peek() {
            return hasNext() ? current : null;
        }

        @Override
        public boolean hasNext() {
            if (current != null) {
                return true;
            }

            while (iterator.hasNext()) {
                Entry<MemorySegment> entry = iterator.next();
                if (!entry.isTombstone()) {
                    this.current = entry;
                    return true;
                }
            }

            return false;
        }

        @Override
        public Entry<MemorySegment> next() {
            if (!hasNext()) {
                throw new NoSuchElementException("...");
            }
            Entry<MemorySegment> next = current;
            current = null;
            return next;
        }
    }
    public static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    public static byte[] getBytes(String message) {
        return message.getBytes(StandardCharsets.UTF_8);
    }

    public static void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                pool.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                    LOG.error("Pool did not terminate");
                }
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }
    public static MemorySegment getKey(String id) {
        return MemorySegment.ofArray(getBytes(id));
    }

    public static byte[] valueToSegment(StoredValue value) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ObjectOutputStream out = new ObjectOutputStream(bos);
            out.writeObject(value);
            out.flush();
            return bos.toByteArray();
        }
    }

    public static StoredValue segmentToValue(byte[] value) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(value);
        try (ObjectInput in = new ObjectInputStream(bis)) {
            return (StoredValue) in.readObject();
        }
    }
}
