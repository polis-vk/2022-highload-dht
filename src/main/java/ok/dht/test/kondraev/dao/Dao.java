package ok.dht.test.kondraev.dao;

import jdk.incubator.foreign.MemorySegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.concurrent.CompletableFuture;
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
    private volatile State state;
    private final ReadWriteLock upsertLock = new ReentrantReadWriteLock();

    private Dao(long flushThresholdBytes, State state) {
        this.flushThresholdBytes = flushThresholdBytes;
        this.state = state;
    }

    /**
     * Constructs Data access object.
     * @param flushThresholdBytes minimal size of MemoryTable to flush it automatically
     * @param basePath folder which used by Dao Storage
     * @throws IOException if any occur when initialize storage
     */
    public static Dao of(long flushThresholdBytes, Path basePath) throws IOException {
        return new Dao(
                flushThresholdBytes,
                State.newState(Storage.load(basePath))
        );
    }

    public Iterator<MemorySegmentEntry> get(MemorySegment from, MemorySegment to) throws IOException {
        if (from == null) {
            return get(MemorySegmentComparator.MINIMAL, to);
        }
        State state = accessState();
        PeekIterator<MemorySegmentEntry> inMemoryIterator = new PeekIterator<>(state.memoryTable.get(from, to));
        Spliterator<SortedStringTable> tableSpliterator = state.storage.spliterator();
        int tablesCount = (int) tableSpliterator.getExactSizeIfKnown();
        if (tablesCount == 0 && state.flushingTable == null) {
            return withoutTombStones(inMemoryIterator);
        }
        List<PeekIterator<MemorySegmentEntry>> iterators =
                new ArrayList<>(1 + (state.flushingTable == null ? 0 : 1) + tablesCount);
        iterators.add(inMemoryIterator);
        if (state.flushingTable != null) {
            iterators.add(new PeekIterator<>(state.flushingTable.get(from, to)));
        }
        tableSpliterator.forEachRemaining(t -> iterators.add(new PeekIterator<>(t.get(from, to))));
        return withoutTombStones(new PeekIterator<>(MergedIterator.of(iterators)));
    }

    static Iterator<MemorySegmentEntry> allStored(Spliterator<SortedStringTable> tableSpliterator) {
        List<PeekIterator<MemorySegmentEntry>> iterators =
                new ArrayList<>((int) tableSpliterator.getExactSizeIfKnown());
        tableSpliterator.forEachRemaining(t ->
                iterators.add(new PeekIterator<>(t.get(MemorySegmentComparator.MINIMAL, null))));
        return withoutTombStones(new PeekIterator<>(MergedIterator.of(iterators)));
    }

    public void upsert(MemorySegmentEntry entry) {
        State state = this.state;
        long byteSizeAfter;
        // it is intentionally the read lock.
        upsertLock.readLock().lock();
        try {
            byteSizeAfter = state.memoryTable.upsert(entry);
        } finally {
            upsertLock.readLock().unlock();
        }
        if (byteSizeAfter >= flushThresholdBytes) {
            Future<?> ignored = flushInBackground(false);
        }
    }

    private Future<?> flushInBackground(boolean tolerantToOngoingFlush) {
        upsertLock.writeLock().lock();
        try {
            State state = accessState();
            if (state.isFlushing()) {
                if (tolerantToOngoingFlush) {
                    return CompletableFuture.completedFuture(null);
                }
                throw new TooManyBackgroundFlushesException();
            }
            state = state.flushing();
            this.state = state;
        } finally {
            upsertLock.writeLock().unlock();
        }

        return backgroundExecutor.submit(() -> {
            try {
                flushImpl();
            } catch (Exception e) {
                LOG.error("Can't flush", e);
                this.state.storage.close();
                throw new RuntimeException(e);
            }
        });
    }

    public MemorySegmentEntry get(MemorySegment key) throws IOException {
        State state = accessState();
        MemorySegmentEntry result = state.memoryTable.get(key);
        if (result != null) {
            return result.isTombStone() ? null : result;
        }
        if (state.isFlushing()) {
            result = state.flushingTable.get(key);
            if (result != null) {
                return result.isTombStone() ? null : result;
            }
        }
        result = state.storage.get(key);
        if (result != null) {
            return result.isTombStone() ? null : result;
        }
        return null;
    }

    public void flush() {
        try {
            flushInBackground(true).get();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void flushImpl() throws IOException {
        State state = accessState();
        if (state.flushingTable.isEmpty()) {
            throw new IllegalStateException("Trying to flush empty MemoryTable");
        }
        Storage storage = state.storage.store(state.flushingTable.values());
        upsertLock.writeLock().lock();
        try {
            state = state.afterFlush(storage);
            this.state = state;
        } finally {
            upsertLock.writeLock().unlock();
        }
    }

    public void compact() {
        State beforeCompactState = accessState();
        if (beforeCompactState.storage.isCompacted()) {
            return;
        }
        try {
            backgroundExecutor.submit(() -> {
                try {
                    compactImpl();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).get();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void compactImpl() throws IOException {
        State state = accessState();
        if (state.storage.isCompacted()) {
            return;
        }
        state.storage.compact(allStored(state.storage.spliterator()));
        finishCompaction();
    }

    public synchronized void close() throws IOException {
        State state = this.state;
        if (state.isClosed) {
            return; // close() is idempotent
        }
        backgroundExecutor.shutdown();
        boolean interrupted = false;
        try {
            interrupted = !backgroundExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            interrupted = true;
        } finally {
            if (!state.memoryTable.isEmpty()) {
                state.storage.store(state.memoryTable.values());
            }
            state.storage.close();
            state = state.afterClosed();
            this.state = state;
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void finishCompaction() throws IOException {
        State state = accessState();
        Storage storage = state.storage.finishCompact();
        upsertLock.writeLock().lock();
        try {
            state = state.afterCompact(storage);
            this.state = state;
        } finally {
            upsertLock.writeLock().unlock();
        }
    }

    private static Iterator<MemorySegmentEntry> withoutTombStones(PeekIterator<MemorySegmentEntry> iterator) {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                while (iterator.hasNext()) {
                    if (!iterator.peek().isTombStone()) {
                        return true;
                    }
                    iterator.next();
                }
                return false;
            }

            @Override
            public MemorySegmentEntry next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return iterator.next();
            }
        };
    }

    private State accessState() {
        State state = this.state;
        state.assertNotClosed();
        return state;
    }

    /*
     * Provides atomic access to MemoryTable + Storage state via
     * `final` guarantees + volatile variable State.
     */
    private static class State {
        final Storage storage;
        final MemoryTable memoryTable;
        final MemoryTable flushingTable;
        final boolean isClosed;

        private State(Storage storage, MemoryTable memoryTable, MemoryTable flushingTable, boolean isClosed) {
            this.storage = storage;
            this.memoryTable = memoryTable;
            this.flushingTable = flushingTable;
            this.isClosed = isClosed;
        }

        boolean isFlushing() {
            return flushingTable != null;
        }

        State afterFlush(Storage storage) {
            assertNotClosed();
            return new State(storage, memoryTable, null, false);
        }

        public void assertNotClosed() {
            if (isClosed) {
                throw new IllegalStateException("Dao is already closed");
            }
        }

        State flushing() {
            assertNotClosed();
            if (isFlushing()) {
                throw new IllegalStateException("Trying to flush twice: already flushing");
            }
            return new State(storage, new MemoryTable(), memoryTable, false);
        }

        State afterClosed() {
            assertNotClosed();
            if (!storage.isClosed()) {
                throw new IllegalStateException("Storage should be closed before Dao");
            }
            return new State(storage, memoryTable, flushingTable, true);
        }

        static State newState(Storage storage) {
            return new State(storage, new MemoryTable(), null, false);
        }

        public State afterCompact(Storage storage) {
            assertNotClosed();
            return new State(storage, memoryTable, flushingTable, false);
        }
    }
}
