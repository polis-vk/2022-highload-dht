package ok.dht.test.kondraev.dao;

import jdk.incubator.foreign.MemorySegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Author: Dmitry Kondraev.
 */
public final class Dao {
    private static final Logger LOG = LoggerFactory.getLogger(Dao.class);

    private final ExecutorService backgroundExecutor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "ConcurrentFilesBackedDaoBackground"));
    private final long flushThresholdBytes;
    private volatile DaoState state;
    private final ReadWriteLock upsertLock = new ReentrantReadWriteLock();

    private Dao(long flushThresholdBytes, DaoState state) {
        this.flushThresholdBytes = flushThresholdBytes;
        this.state = state;
    }

    /**
     * Constructs Data access object.
     *
     * @param flushThresholdBytes minimal size of MemoryTable to flush it automatically
     * @param basePath folder which used by Dao Storage
     * @throws IOException if any occur when initialize storage
     */
    public static Dao of(long flushThresholdBytes, Path basePath) throws IOException {
        return new Dao(
                flushThresholdBytes,
                DaoState.newState(Storage.load(basePath))
        );
    }

    public Iterator<MemorySegmentEntry> get(MemorySegment from, MemorySegment to) throws IOException {
        if (from == null) {
            return get(MemorySegmentComparator.MINIMAL, to);
        }
        DaoState theState = accessState();
        PeekIterator<MemorySegmentEntry> inMemoryIterator = new PeekIterator<>(theState.memoryTable.get(from, to));
        Spliterator<SortedStringTable> tableSpliterator = theState.storage.spliterator();
        int tablesCount = (int) tableSpliterator.getExactSizeIfKnown();
        if (tablesCount == 0 && theState.flushingTable == null) {
            return new WithoutTombstonesIterator(inMemoryIterator);
        }
        List<PeekIterator<MemorySegmentEntry>> iterators =
                new ArrayList<>(1 + (theState.flushingTable == null ? 0 : 1) + tablesCount);
        iterators.add(inMemoryIterator);
        if (theState.flushingTable != null) {
            iterators.add(new PeekIterator<>(theState.flushingTable.get(from, to)));
        }
        tableSpliterator.forEachRemaining(t -> iterators.add(new PeekIterator<>(t.get(from, to))));
        return new WithoutTombstonesIterator(new PeekIterator<>(MergedIterator.of(iterators)));
    }

    static Iterator<MemorySegmentEntry> allStored(Spliterator<SortedStringTable> tableSpliterator) {
        List<PeekIterator<MemorySegmentEntry>> iterators =
                new ArrayList<>((int) tableSpliterator.getExactSizeIfKnown());
        tableSpliterator.forEachRemaining(t ->
                iterators.add(new PeekIterator<>(t.get(MemorySegmentComparator.MINIMAL, null))));
        return new WithoutTombstonesIterator(new PeekIterator<>(MergedIterator.of(iterators)));
    }

    public void upsert(MemorySegmentEntry entry) {
        DaoState theState = accessState();
        long byteSizeAfter;
        // it is intentionally the read lock.
        upsertLock.readLock().lock();
        try {
            byteSizeAfter = theState.memoryTable.upsert(entry);
        } finally {
            upsertLock.readLock().unlock();
        }
        if (byteSizeAfter < flushThresholdBytes) {
            return;
        }
        upsertLock.writeLock().lock();
        try {
            theState = accessState();
            if (theState.isFlushing()) {
                throw new TooManyBackgroundFlushesException();
            }
            theState = theState.flushing();
            state = theState;
        } finally {
            upsertLock.writeLock().unlock();
        }

        backgroundExecutor.execute(this::flushImpl);
    }

    public MemorySegmentEntry get(MemorySegment key) throws IOException {
        DaoState theState = accessState();
        MemorySegmentEntry result = theState.memoryTable.get(key);
        if (result != null) {
            return result.isTombStone() ? null : result;
        }
        if (theState.isFlushing()) {
            result = theState.flushingTable.get(key);
            if (result != null) {
                return result.isTombStone() ? null : result;
            }
        }
        result = theState.storage.get(key);
        if (result != null) {
            return result.isTombStone() ? null : result;
        }
        return null;
    }

    public void flush() {
        upsertLock.writeLock().lock();
        try {
            DaoState theState = accessState();
            if (theState.isFlushing()) {
                return;
            } else {
                theState = theState.flushing();
                state = theState;
            }
        } finally {
            upsertLock.writeLock().unlock();
        }

        Future<?> act = backgroundExecutor.submit(this::flushImpl);
        getAndUnwrap(act);
    }

    private void flushImpl() {
        try {
            DaoState theState = accessState();
            if (theState.flushingTable.isEmpty()) {
                throw new IllegalStateException("Trying to flush empty MemoryTable");
            }
            Storage storage = theState.storage.store(theState.flushingTable.values());
            upsertLock.writeLock().lock();
            try {
                theState = theState.afterFlush(storage);
                state = theState;
            } finally {
                upsertLock.writeLock().unlock();
            }
        } catch (Exception e) {
            LOG.error("Can't flush", e);
            state.storage.close();
            throw new RuntimeException(e);
        }

    }

    public void compact() {
        DaoState beforeCompactState = accessState();
        if (beforeCompactState.storage.isCompacted()) {
            return;
        }
        Future<?> act = backgroundExecutor.submit(() -> {
            try {
                compactImpl();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        getAndUnwrap(act);
    }

    private void getAndUnwrap(Future<?> act) {
        try {
            act.get();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void compactImpl() throws IOException {
        DaoState theState = accessState();
        if (theState.storage.isCompacted()) {
            return;
        }
        theState.storage.compact(allStored(theState.storage.spliterator()));
        finishCompaction();
    }

    public synchronized void close() throws IOException {
        DaoState theState = state;
        if (theState.isClosed) {
            return; // close() is idempotent
        }
        backgroundExecutor.shutdown();
        boolean interrupted = false;
        try {
            interrupted = !backgroundExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            interrupted = true;
        } finally {
            if (!theState.memoryTable.isEmpty()) {
                theState.storage.store(theState.memoryTable.values());
            }
            theState.storage.close();
            theState = theState.afterClosed();
            state = theState;
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void finishCompaction() throws IOException {
        DaoState theState = accessState();
        Storage storage = theState.storage.finishCompact();
        upsertLock.writeLock().lock();
        try {
            theState = theState.afterCompact(storage);
            state = theState;
        } finally {
            upsertLock.writeLock().unlock();
        }
    }

    private DaoState accessState() {
        DaoState theState = state;
        theState.assertNotClosed();
        return theState;
    }
}
