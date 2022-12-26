package ok.dht.test.ponomarev.dao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.ponomarev.dao.store.Storage;

//TODO: Мне не нравится, что конструктор занимается созданием всякого. 
// МБ стоит сделать явные контракты. В коде хер разберешься
public class MemorySegmentDao implements Dao<MemorySegment, TimestampEntry> {
    private final Storage storage;
    private final long sizeLimit;
    private final ExecutorService executorService;

    private final Object flushLock = new Object();

    public MemorySegmentDao(Path path, long limitBytes) throws IOException {
        if (Files.notExists(path)) {
            throw new IllegalArgumentException("Path: " + path + " is not exist");
        }

        this.sizeLimit = limitBytes;
        this.storage = new Storage(path);
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
        final long entrySize = entry.getSizeBytes();
        if (entrySize + storage.getMemTableDataSize() >= sizeLimit) {
            synchronized (flushLock) {
                if (entrySize + storage.getMemTableDataSize() >= sizeLimit) {
                    storage.beforeFlush();
                    executorService.execute(this::handleFlush);
                }
            }
        }

        storage.upsert(entry);
    }

    private void handleFlush() {
        synchronized (flushLock) {
            try {
                storage.flush(System.currentTimeMillis());
                storage.afterFlush();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
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
        executorService.execute(this::handleCompact);
    }

    private void handleCompact() {
        synchronized (flushLock) {
            try {
                storage.beforeFlush();
                storage.compact(System.currentTimeMillis());
                storage.afterFlush();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Override
    public void flush() throws IOException {
        synchronized (flushLock) {
            storage.beforeFlush();
            storage.flush(
                    System.currentTimeMillis()
            );
            storage.afterFlush();
        }
    }
}
