package ok.dht.test.kazakov.dao;

import jdk.incubator.foreign.MemorySegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
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

    private static final MemorySegment VERY_FIRST_KEY = MemorySegment.ofArray(new byte[]{});

    private final ReadWriteLock upsertLock = new ReentrantReadWriteLock();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "MemorySegmentDaoBG"));

    private volatile DaoState state;

    private final Config config;

    public MemorySegmentDao(final Config config) throws IOException {
        this.config = config;
        this.state = DaoState.newState(config, Storage.load(config));
    }

    @Override
    public Iterator<Entry<MemorySegment>> get(final MemorySegment from, final MemorySegment to) {
        final MemorySegment internalFrom;
        internalFrom = Objects.requireNonNullElse(from, VERY_FIRST_KEY);
        return getTombstoneFilteringIterator(internalFrom, to);
    }

    private TombstoneFilteringIterator getTombstoneFilteringIterator(final MemorySegment from, final MemorySegment to) {
        final DaoState freezedState = accessState();

        final List<Iterator<Entry<MemorySegment>>> iterators = freezedState.storage.iterate(from, to);
        iterators.add(freezedState.flushing.get(from, to));
        iterators.add(freezedState.memory.get(from, to));

        final Iterator<Entry<MemorySegment>> mergeIterator = MergeIterator.of(iterators, EntryKeyComparator.INSTANCE);
        return new TombstoneFilteringIterator(mergeIterator);
    }

    @Override
    public Entry<MemorySegment> get(final MemorySegment key) {
        final DaoState freezedState = accessState();

        Entry<MemorySegment> result = freezedState.memory.get(key);
        if (result == null) {
            result = freezedState.flushing.get(key);
        }
        if (result == null) {
            result = freezedState.storage.get(key);
        }

        return (result == null || result.isTombstone()) ? null : result;
    }

    // we do not want to wait for background flush here, so future is ignored
    // in case of error during flush error message is logged and storage is closed
    @SuppressWarnings("FutureReturnValueIgnored")
    @Override
    public void upsert(final Entry<MemorySegment> entry) {
        final DaoState freezedState = accessState();

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
                DaoState freezedState = accessState();
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
                final DaoState freezedState = accessState();

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
        final DaoState preCompactState = accessState();
        if (preCompactState.memory.isEmpty() && preCompactState.storage.isCompacted()) {
            return;
        }

        final Future<Object> future = executor.submit(() -> {
            final DaoState freezedState = accessState();
            if (freezedState.memory.isEmpty() && freezedState.storage.isCompacted()) {
                return null;
            }
            StorageHelper.compact(
                    config,
                    () -> MergeIterator.of(
                            freezedState.storage.iterate(VERY_FIRST_KEY, null),
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
                throw new IllegalStateException("Unknown exception was thrown", e);
            }
        }
    }

    private DaoState accessState() {
        final DaoState freezedState = this.state;
        if (freezedState.closed) {
            throw new IllegalStateException("Dao is already closed");
        }
        return freezedState;
    }

    @Override
    public synchronized void close() throws IOException {
        DaoState freezedState = this.state;
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
}
