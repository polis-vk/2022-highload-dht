package ok.dht.test.pobedonostsev.dao;

import jdk.incubator.foreign.MemorySegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MemorySegmentDao implements Dao<MemorySegment, Entry<MemorySegment>> {

    private static final Logger LOG = LoggerFactory.getLogger(MemorySegmentDao.class);

    private static final MemorySegment VERY_FIRST_KEY = MemorySegment.ofArray(new byte[] {});

    private final ReadWriteLock upsertLock = new ReentrantReadWriteLock();

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "MemorySegmentDaoBG"));
    private final Config config;
    private volatile State state;

    public MemorySegmentDao(Config config) throws IOException {
        this.config = config;
        this.state = State.newState(config, Storage.load(config));
    }

    @Override
    public Iterator<Entry<MemorySegment>> get(MemorySegment from, MemorySegment to) {
        return getTombstoneFilteringIterator(Objects.requireNonNullElse(from, VERY_FIRST_KEY), to);
    }

    private TombstoneFilteringIterator getTombstoneFilteringIterator(MemorySegment from, MemorySegment to) {
        State stateRef = accessState();

        ArrayList<Iterator<Entry<MemorySegment>>> iterators =
                (ArrayList<Iterator<Entry<MemorySegment>>>) stateRef.storage.iterate(from, to);

        iterators.add(stateRef.flushing.get(from, to));
        iterators.add(stateRef.memory.get(from, to));

        Iterator<Entry<MemorySegment>> mergeIterator = MergeIterator.of(iterators, EntryKeyComparator.INSTANCE);

        return new TombstoneFilteringIterator(mergeIterator);
    }

    @Override
    public Entry<MemorySegment> get(MemorySegment key) {
        State stateRef = accessState();

        Entry<MemorySegment> result = stateRef.memory.get(key);
        if (result == null) {
            result = stateRef.storage.get(key);
        }

        return (result == null || result.isTombstone()) ? null : result;
    }

    @Override
    public void upsert(Entry<MemorySegment> entry) {
        State stateRef = accessState();

        boolean runFlush;
        // it is intentionally the read lock!!!
        upsertLock.readLock().lock();
        try {
            runFlush = stateRef.memory.put(entry.key(), entry);
        } finally {
            upsertLock.readLock().unlock();
        }

        if (runFlush) {
            flushInBg(false);
        }
    }

    private Future<?> flushInBg(boolean tolerateFlushInProgress) {
        upsertLock.writeLock().lock();
        try {
            State stateRef = accessState();
            if (stateRef.isFlushing()) {
                if (tolerateFlushInProgress) {
                    // or any other completed future
                    return CompletableFuture.completedFuture(null);
                }
                throw new TooManyFlushesInBgException();
            }

            stateRef = stateRef.prepareForFlush();
            this.state = stateRef;
        } finally {
            upsertLock.writeLock().unlock();
        }

        return executor.submit(() -> {
            try {
                State stateRef = accessState();

                Storage storage = stateRef.storage;
                Storage.save(config, storage, stateRef.flushing.values());
                Storage load = Storage.load(config);

                upsertLock.writeLock().lock();
                try {
                    this.state = stateRef.afterFlush(load);
                } finally {
                    upsertLock.writeLock().unlock();
                }
                return null;
            } catch (Exception e) {
                LOG.error("Can't flush", e);
                try {
                    this.state.storage.close();
                } catch (IOException ex) {
                    LOG.error("Can't stop storage", ex);
                    ex.addSuppressed(e);
                    throw ex;
                }
                throw e;
            }
        });
    }

    @Override
    public void flush() throws IOException {
        boolean runFlush;
        // it is intentionally the read lock!!!
        upsertLock.writeLock().lock();
        try {
            runFlush = state.memory.overflow();
        } finally {
            upsertLock.writeLock().unlock();
        }

        if (runFlush) {
            Future<?> future = flushInBg(true);
            awaitAndUnwrap(future);
        }
    }

    @Override
    public void compact() throws IOException {
        State preCompactState = accessState();

        if (preCompactState.memory.isEmpty() && preCompactState.storage.isCompacted()) {
            return;
        }

        Future<Object> future = executor.submit(() -> {
            State stateRef = accessState();

            if (stateRef.memory.isEmpty() && stateRef.storage.isCompacted()) {
                return null;
            }

            Storage.compact(config, () -> MergeIterator.of(stateRef.storage.iterate(VERY_FIRST_KEY, null),
                    EntryKeyComparator.INSTANCE));

            Storage storage = Storage.load(config);

            upsertLock.writeLock().lock();
            try {
                this.state = stateRef.afterCompact(storage);
            } finally {
                upsertLock.writeLock().unlock();
            }
            return null;
        });

        awaitAndUnwrap(future);
    }

    private void awaitAndUnwrap(Future<?> future) {
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private State accessState() {
        State stateRef = this.state;
        if (stateRef.closed) {
            throw new IllegalStateException("Dao is already closed");
        }
        return stateRef;
    }

    @Override
    public synchronized void close() throws IOException {
        State stateRef = this.state;
        if (stateRef.closed) {
            return;
        }
        executor.shutdown();
        try {
            boolean result = executor.awaitTermination(10, TimeUnit.DAYS);
            while (!result) {
                result = executor.awaitTermination(10, TimeUnit.DAYS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        stateRef = this.state;
        stateRef.storage.close();
        this.state = stateRef.afterClosed();
        if (stateRef.memory.isEmpty()) {
            return;
        }
        Storage.save(config, stateRef.storage, stateRef.memory.values());
    }

    private static class State {
        final Config config;
        final Memory memory;
        final Memory flushing;
        final Storage storage;
        final boolean closed;

        State(Config config, Memory memory, Memory flushing, Storage storage) {
            this.config = config;
            this.memory = memory;
            this.flushing = flushing;
            this.storage = storage;
            this.closed = false;
        }

        State(Config config, Storage storage, boolean closed) {
            this.config = config;
            this.memory = Memory.EMPTY;
            this.flushing = Memory.EMPTY;
            this.storage = storage;
            this.closed = closed;
        }

        static State newState(Config config, Storage storage) {
            return new State(config, new Memory(config.flushThresholdBytes()), Memory.EMPTY, storage);
        }

        public State prepareForFlush() {
            checkNotClosed();
            if (isFlushing()) {
                throw new IllegalStateException("Already flushing");
            }
            return new State(config, new Memory(config.flushThresholdBytes()), memory, storage);
        }

        public State afterFlush(Storage storage) {
            checkNotClosed();
            if (!isFlushing()) {
                throw new IllegalStateException("Wasn't flushing");
            }
            return new State(config, memory, Memory.EMPTY, storage);
        }

        public State afterCompact(Storage storage) {
            checkNotClosed();
            return new State(config, memory, flushing, storage);
        }

        public State afterClosed() {
            checkNotClosed();
            if (!storage.isClosed()) {
                throw new IllegalStateException("Storage should be closed early");
            }
            return new State(config, storage, true);
        }

        public void checkNotClosed() {
            if (closed) {
                throw new IllegalStateException("Already closed");
            }
        }

        public boolean isFlushing() {
            return this.flushing != Memory.EMPTY;
        }
    }
}
