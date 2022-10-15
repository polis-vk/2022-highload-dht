package ok.dht.test.pashchenko.dao;

import jdk.incubator.foreign.MemorySegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MemorySegmentDao {

    private static final Logger LOG = LoggerFactory.getLogger(MemorySegmentDao.class);

    private static final MemorySegment VERY_FIRST_KEY = MemorySegment.ofArray(new byte[]{});

    private final ReadWriteLock upsertLock = new ReentrantReadWriteLock();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "MemorySegmentDaoBG"));

    private volatile State state;

    private final Config config;

    public MemorySegmentDao(Config config) throws IOException {
        this.config = config;
        this.state = State.newState(config, Storage.load(config));
    }

    public Iterator<Entry> get(MemorySegment from, MemorySegment to) {
        if (from == null) {
            from = VERY_FIRST_KEY;
        }

        return getTombstoneFilteringIterator(from, to);
    }

    private TombstoneFilteringIterator getTombstoneFilteringIterator(MemorySegment from, MemorySegment to) {
        State state = accessState();

        ArrayList<Iterator<Entry>> iterators = state.storage.iterate(from, to);

        iterators.add(state.flushing.get(from, to));
        iterators.add(state.memory.get(from, to));

        Iterator<Entry> mergeIterator = MergeIterator.of(iterators, EntryKeyComparator.INSTANCE);

        return new TombstoneFilteringIterator(mergeIterator);
    }

    public Entry get(MemorySegment key) {
        State state = accessState();

        Entry result = state.memory.get(key);
        if (result == null) {
            result = state.storage.get(key);
        }

        return (result == null || result.isTombstone()) ? null : result;
    }

    public void upsert(Entry entry) {
        State state = accessState();

        boolean runFlush;
        // it is intentionally the read lock!!!
        upsertLock.readLock().lock();
        try {
            runFlush = state.memory.put(entry.key(), entry);
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
            State state = accessState();
            if (state.isFlushing()) {
                if (tolerateFlushInProgress) {
                    // or any other completed future
                    return CompletableFuture.completedFuture(null);
                }
                throw new TooManyFlushesInBgException();
            }

            state = state.prepareForFlush();
            this.state = state;
        } finally {
            upsertLock.writeLock().unlock();
        }

        return executor.submit(() -> {
            try {
                State state = accessState();

                Storage storage = state.storage;
                Storage.save(config, storage, state.flushing.values());
                Storage load = Storage.load(config);

                upsertLock.writeLock().lock();
                try {
                    this.state = state.afterFlush(load);
                } finally {
                    upsertLock.writeLock().unlock();
                }
                storage.maybeClose();
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

    public void compact() throws IOException {
        State preCompactState = accessState();

        if (preCompactState.memory.isEmpty() && preCompactState.storage.isCompacted()) {
            return;
        }

        Future<Object> future = executor.submit(() -> {
            State state = accessState();

            if (state.memory.isEmpty() && state.storage.isCompacted()) {
                return null;
            }

            Storage.compact(
                    config,
                    () -> MergeIterator.of(
                            state.storage.iterate(VERY_FIRST_KEY,
                                    null
                            ),
                            EntryKeyComparator.INSTANCE
                    )
            );

            Storage storage = Storage.load(config);

            upsertLock.writeLock().lock();
            try {
                this.state = state.afterCompact(storage);
            } finally {
                upsertLock.writeLock().unlock();
            }

            state.storage.maybeClose();
            return null;
        });

        awaitAndUnwrap(future);
    }

    private void awaitAndUnwrap(Future<?> future) throws IOException {
        try {
            future.get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            try {
                throw e.getCause();
            } catch (RuntimeException | IOException | Error r) {
                throw r;
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }

    private State accessState() {
        State state = this.state;
        if (state.closed) {
            throw new IllegalStateException("Dao is already closed");
        }
        return state;
    }

    public synchronized void close() throws IOException {
        State state = this.state;
        if (state.closed) {
            return;
        }
        executor.shutdown();
        try {
            //noinspection StatementWithEmptyBody
            while (!executor.awaitTermination(10, TimeUnit.DAYS)) ;
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        state = this.state;
        state.storage.close();
        this.state = state.afterClosed();
        if (state.memory.isEmpty()) {
            return;
        }
        Storage.save(config, state.storage, state.memory.values());
    }

    private static class TombstoneFilteringIterator implements Iterator<Entry> {
        private final Iterator<Entry> iterator;
        private Entry current;

        public TombstoneFilteringIterator(Iterator<Entry> iterator) {
            this.iterator = iterator;
        }

        public Entry peek() {
            return hasNext() ? current : null;
        }

        @Override
        public boolean hasNext() {
            if (current != null) {
                return true;
            }

            while (iterator.hasNext()) {
                Entry entry = iterator.next();
                if (!entry.isTombstone()) {
                    this.current = entry;
                    return true;
                }
            }

            return false;
        }

        @Override
        public Entry next() {
            if (!hasNext()) {
                throw new NoSuchElementException("...");
            }
            Entry next = current;
            current = null;
            return next;
        }
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
            return new State(
                    config,
                    new Memory(config.flushThresholdBytes()),
                    Memory.EMPTY,
                    storage
            );
        }

        public State prepareForFlush() {
            checkNotClosed();
            if (isFlushing()) {
                throw new IllegalStateException("Already flushing");
            }
            return new State(
                    config,
                    new Memory(config.flushThresholdBytes()),
                    memory,
                    storage
            );
        }

        public State afterFlush(Storage storage) {
            checkNotClosed();
            if (!isFlushing()) {
                throw new IllegalStateException("Wasn't flushing");
            }
            return new State(
                    config,
                    memory,
                    Memory.EMPTY,
                    storage
            );
        }

        public State afterCompact(Storage storage) {
            checkNotClosed();
            return new State(
                    config,
                    memory,
                    flushing,
                    storage
            );
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

    private static class Memory {

        static final Memory EMPTY = new Memory(-1);
        private final AtomicLong size = new AtomicLong();
        private final AtomicBoolean oversized = new AtomicBoolean();

        private final ConcurrentSkipListMap<MemorySegment, Entry> delegate =
                new ConcurrentSkipListMap<>(MemorySegmentComparator.INSTANCE);

        private final long sizeThreshold;

        Memory(long sizeThreshold) {
            this.sizeThreshold = sizeThreshold;
        }

        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        public Collection<Entry> values() {
            return delegate.values();
        }

        public boolean put(MemorySegment key, Entry entry) {
            if (sizeThreshold == -1) {
                throw new UnsupportedOperationException("Read-only map");
            }
            Entry segmentEntry = delegate.put(key, entry);
            long sizeDelta = Storage.getSizeOnDisk(entry);
            if (segmentEntry != null) {
                sizeDelta -= Storage.getSizeOnDisk(segmentEntry);
            }
            long newSize = size.addAndGet(sizeDelta);
            if (newSize > sizeThreshold) {
                return !oversized.getAndSet(true);
            }
            return false;
        }

        public boolean overflow() {
            return !oversized.getAndSet(true);
        }

        public Iterator<Entry> get(MemorySegment from, MemorySegment to) {
            return to == null
                    ? delegate.tailMap(from).values().iterator()
                    : delegate.subMap(from, to).values().iterator();
        }

        public Entry get(MemorySegment key) {
            return delegate.get(key);
        }
    }


}
