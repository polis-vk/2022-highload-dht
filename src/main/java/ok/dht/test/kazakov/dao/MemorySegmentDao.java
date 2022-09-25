package ok.dht.test.kazakov.dao;

import jdk.incubator.foreign.MemorySegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MemorySegmentDao implements Dao<MemorySegment, Entry<MemorySegment>> {

    private static final Logger LOG = LoggerFactory.getLogger(MemorySegmentDao.class);

    private static final MemorySegment VERY_FIRST_KEY = MemorySegment.ofArray(new byte[]{});

    private final ReadWriteLock upsertLock = new ReentrantReadWriteLock();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "MemorySegmentDaoBG"));

    private volatile State state;

    private final Config config;

    public MemorySegmentDao(final Config config) throws IOException {
        this.config = config;
        this.state = State.newState(config, Storage.load(config));
    }

    @Override
    public Iterator<Entry<MemorySegment>> get(final MemorySegment from, final MemorySegment to) {
        final MemorySegment internalFrom;
        internalFrom = Objects.requireNonNullElse(from, VERY_FIRST_KEY);

        return getTombstoneFilteringIterator(internalFrom, to);
    }

    private TombstoneFilteringIterator getTombstoneFilteringIterator(final MemorySegment from, final MemorySegment to) {
        final State freezedState = accessState();

        final List<Iterator<Entry<MemorySegment>>> iterators = freezedState.storage.iterate(from, to);

        iterators.add(freezedState.flushing.get(from, to));
        iterators.add(freezedState.memory.get(from, to));

        final Iterator<Entry<MemorySegment>> mergeIterator = MergeIterator.of(iterators, EntryKeyComparator.INSTANCE);

        return new TombstoneFilteringIterator(mergeIterator);
    }

    @Override
    public Entry<MemorySegment> get(final MemorySegment key) {
        final State freezedState = accessState();

        Entry<MemorySegment> result = freezedState.memory.get(key);
        if (result == null) {
            result = freezedState.flushing.get(key);
        }

        if (result == null) {
            result = freezedState.storage.get(key);
        }

        return (result == null || result.isTombstone()) ? null : result;
    }

    @Override
    public void upsert(final Entry<MemorySegment> entry) {
        final State freezedState = accessState();

        boolean runFlush;
        // it is intentionally the read lock!!!
        upsertLock.readLock().lock();
        try {
            runFlush = freezedState.memory.put(entry.key(), entry);
        } finally {
            upsertLock.readLock().unlock();
        }

        if (runFlush) {
            flushInBg(false);
        }
    }

    private Future<?> flushInBg(final boolean tolerateFlushInProgress) {
        while (true) {
            upsertLock.writeLock().lock();
            try {
                State freezedState = accessState();
                if (freezedState.isFlushing()) {
                    if (tolerateFlushInProgress) {
                        // or any other completed future
                        return CompletableFuture.completedFuture(null);
                    }
                    continue;
                }

                freezedState = freezedState.prepareForFlush();
                this.state = freezedState;
                break;
            } finally {
                upsertLock.writeLock().unlock();
            }
        }

        return executor.submit(() -> {
            try {
                final State freezedState = accessState();

                final Storage storage = freezedState.storage;
                Storage.save(config, storage, freezedState.flushing.values());
                final Storage load = Storage.load(config);

                upsertLock.writeLock().lock();
                try {
                    this.state = freezedState.afterFlush(load);
                } finally {
                    upsertLock.writeLock().unlock();
                }
                storage.maybeClose();
                return null;
            } catch (final Exception e) {
                LOG.error("Can't flush", e);
                try {
                    this.state.storage.close();
                } catch (final IOException ex) {
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
            final Future<?> future = flushInBg(true);
            awaitAndUnwrap(future);
        }
    }

    @Override
    public void compact() throws IOException {
        final State preCompactState = accessState();

        if (preCompactState.memory.isEmpty() && preCompactState.storage.isCompacted()) {
            return;
        }

        final Future<Object> future = executor.submit(() -> {
            final State freezedState = accessState();

            if (freezedState.memory.isEmpty() && freezedState.storage.isCompacted()) {
                return null;
            }

            Storage.compact(
                    config,
                    () -> MergeIterator.of(
                            freezedState.storage.iterate(VERY_FIRST_KEY,
                                    null
                            ),
                            EntryKeyComparator.INSTANCE
                    )
            );

            final Storage storage = Storage.load(config);

            upsertLock.writeLock().lock();
            try {
                this.state = freezedState.afterCompact(storage);
            } finally {
                upsertLock.writeLock().unlock();
            }

            freezedState.storage.maybeClose();
            return null;
        });

        awaitAndUnwrap(future);
    }

    private void awaitAndUnwrap(final Future<?> future) throws IOException {
        try {
            future.get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (final ExecutionException e) {
            final Throwable cause = e.getCause();

            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else if (cause instanceof IOException) {
                throw (IOException) cause;
            } else if (cause instanceof Error) {
                throw (Error) cause;
            } else {
                throw new RuntimeException(cause);
            }
        }
    }

    private State accessState() {
        final State freezedState = this.state;
        if (freezedState.closed) {
            throw new IllegalStateException("Dao is already closed");
        }
        return freezedState;
    }

    @Override
    public synchronized void close() throws IOException {
        State freezedState = this.state;
        if (freezedState.closed) {
            return;
        }
        executor.shutdown();
        try {
            while (true) {
                if (executor.awaitTermination(10, TimeUnit.DAYS)) {
                    break;
                }
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        freezedState = this.state;
        freezedState.storage.close();
        this.state = freezedState.afterClosed();
        if (freezedState.memory.isEmpty()) {
            return;
        }
        Storage.save(config, freezedState.storage, freezedState.memory.values());
    }

    private static class TombstoneFilteringIterator implements Iterator<Entry<MemorySegment>> {
        private final Iterator<Entry<MemorySegment>> iterator;
        private Entry<MemorySegment> current;

        public TombstoneFilteringIterator(final Iterator<Entry<MemorySegment>> iterator) {
            this.iterator = iterator;
        }

        @Override
        public boolean hasNext() {
            if (current != null) {
                return true;
            }

            while (iterator.hasNext()) {
                final Entry<MemorySegment> entry = iterator.next();
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
            final Entry<MemorySegment> next = current;
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

        State(final Config config, final Memory memory, final Memory flushing, final Storage storage) {
            this.config = config;
            this.memory = memory;
            this.flushing = flushing;
            this.storage = storage;
            this.closed = false;
        }

        State(final Config config, final Storage storage, final boolean closed) {
            this.config = config;
            this.memory = Memory.EMPTY;
            this.flushing = Memory.EMPTY;
            this.storage = storage;
            this.closed = closed;
        }

        static State newState(final Config config, final Storage storage) {
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

        public State afterFlush(final Storage storage) {
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

        public State afterCompact(final Storage storage) {
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

        private final ConcurrentSkipListMap<MemorySegment, Entry<MemorySegment>> delegate =
                new ConcurrentSkipListMap<>(MemorySegmentComparator.INSTANCE);

        private final long sizeThreshold;

        Memory(final long sizeThreshold) {
            this.sizeThreshold = sizeThreshold;
        }

        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        public Collection<Entry<MemorySegment>> values() {
            return delegate.values();
        }

        public boolean put(final MemorySegment key, final Entry<MemorySegment> entry) {
            if (sizeThreshold == -1) {
                throw new UnsupportedOperationException("Read-only map");
            }
            final Entry<MemorySegment> segmentEntry = delegate.put(key, entry);
            long sizeDelta = Storage.getSizeOnDisk(entry);
            if (segmentEntry != null) {
                sizeDelta -= Storage.getSizeOnDisk(segmentEntry);
            }
            final long newSize = size.addAndGet(sizeDelta);
            if (newSize > sizeThreshold) {
                return !oversized.getAndSet(true);
            }
            return false;
        }

        public boolean overflow() {
            return !oversized.getAndSet(true);
        }

        public Iterator<Entry<MemorySegment>> get(final MemorySegment from, final MemorySegment to) {
            return to == null
                    ? delegate.tailMap(from).values().iterator()
                    : delegate.subMap(from, to).values().iterator();
        }

        public Entry<MemorySegment> get(final MemorySegment key) {
            return delegate.get(key);
        }
    }
}
