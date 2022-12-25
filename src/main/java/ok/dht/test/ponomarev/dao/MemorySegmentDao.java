package ok.dht.test.ponomarev.dao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.ponomarev.dao.store.Storage;

//TODO: Мне не нравится, что конструктор занимается созданием всякого. 
// МБ стоит сделать явные контракты. В коде хер разберешься
public class MemorySegmentDao implements Dao<MemorySegment, TimestampEntry> {
    private final Storage storage;
    private final AtomicLong sizeBytes;
    private final ExecutorService executorService;
    private final long limitBytes;

    public MemorySegmentDao(Path path, long limitBytes) throws IOException {
        if (Files.notExists(path)) {
            throw new IllegalArgumentException("Path: " + path + " is not exist");
        }

        this.limitBytes = limitBytes;
        this.storage = new Storage(path);
        this.sizeBytes = new AtomicLong(0);
        this.executorService = Executors.newFixedThreadPool(2);
    }

    @Override
    public Iterator<TimestampEntry> get(MemorySegment from, MemorySegment to) throws IOException {
        return new TombstoneSkipIterator<>(storage.get(from, to));
    }

    @Override
    public TimestampEntry get(MemorySegment key) throws IOException {
        return storage.get(key);
    }

    @Override
    public void upsert(TimestampEntry entry) {
        final long currentStoreSizeBytes = sizeBytes.addAndGet(entry.getSizeBytes());
        storage.upsert(entry);
        if (currentStoreSizeBytes < limitBytes) {
            return;
        }

        scheduleFlush(currentStoreSizeBytes);
    }

    private synchronized void scheduleFlush(long flushingSize) {
        sizeBytes.addAndGet(-flushingSize);
        if (sizeBytes.get() < limitBytes) {
            return;
        }

        executorService.execute(() -> handleFlush(flushingSize));
    }

    private void handleFlush(long expectedFlushingSize) {
        try {
            final long flushedSizeBytes = storage.flush(System.currentTimeMillis());

            if (flushedSizeBytes > 0) {
                final long diff = flushedSizeBytes - expectedFlushingSize;
                sizeBytes.addAndGet(-diff);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() throws IOException {
        executorService.shutdown();

        try {
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }

        flush();
        storage.close();
    }

    @Override
    public void compact() {
        executorService.execute(
            () -> {
                try {
                    storage.compact(System.currentTimeMillis());
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        );
    }

    @Override
    public void flush() throws IOException {
        storage.flush(
            System.currentTimeMillis()
        );
    }
}
