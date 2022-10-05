package ok.dht.test.nadutkin.database.impl;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.nadutkin.database.Config;
import ok.dht.test.nadutkin.database.Dao;
import ok.dht.test.nadutkin.database.Entry;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MemorySegmentDao implements Dao<MemorySegment, Entry<MemorySegment>> {

    private static final MemorySegment VERY_FIRST_KEY = MemorySegment.ofArray(new byte[]{});

    private final ReadWriteLock upsertLock = new ReentrantReadWriteLock();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "MemorySegmentDaoBG"));

    private volatile UtilsClass.State state;

    private final Config config;

    public MemorySegmentDao(Config config) throws IOException {
        this.config = config;
        this.state = UtilsClass.State.newState(config, StorageMethods.load(config));
    }

    @Override
    public Iterator<Entry<MemorySegment>> get(MemorySegment start, MemorySegment finish) {
        if (start == null) {
            getTombstoneFilteringIterator(VERY_FIRST_KEY, finish);
        }

        return getTombstoneFilteringIterator(start, finish);
    }

    private UtilsClass.TombstoneFilteringIterator getTombstoneFilteringIterator(MemorySegment start,
                                                                                MemorySegment finish) {
        UtilsClass.State accessState = accessState();

        List<Iterator<Entry<MemorySegment>>> iterators = accessState.storage.iterate(start, finish);

        iterators.add(accessState.flushing.get(start, finish));
        iterators.add(accessState.memory.get(start, finish));

        Iterator<Entry<MemorySegment>> mergeIterator = MergeIterator.of(iterators, EntryKeyComparator.INSTANCE);

        return new UtilsClass.TombstoneFilteringIterator(mergeIterator);
    }

    @Override
    public Entry<MemorySegment> get(MemorySegment key) {
        UtilsClass.State accessState = accessState();

        Entry<MemorySegment> result = accessState.memory.get(key);
        if (result == null) {
            result = accessState.storage.get(key);
        }

        return (result == null || result.isTombstone()) ? null : result;
    }

    @Override
    public void upsert(Entry<MemorySegment> entry) {
        UtilsClass.State accessState = accessState();

        boolean runFlush;
        // it is intentionally the read lock!!!
        upsertLock.readLock().lock();
        try {
            runFlush = accessState.memory.put(entry.key(), entry);
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
            UtilsClass.State accessState = accessState();
            if (accessState.isFlushing()) {
                if (tolerateFlushInProgress) {
                    // or any other completed future
                    return CompletableFuture.completedFuture(null);
                }
                throw new TooManyFlushesInBgException();
            }

            accessState = accessState.prepareForFlush();
            this.state = accessState;
        } finally {
            upsertLock.writeLock().unlock();
        }

        return executor.submit(() -> {
            try {
                UtilsClass.State accessState = accessState();

                Storage storage = accessState.storage;
                StorageMethods.save(config, storage, accessState.flushing.values());
                Storage load = StorageMethods.load(config);

                upsertLock.writeLock().lock();
                try {
                    this.state = accessState.afterFlush(load);
                } finally {
                    upsertLock.writeLock().unlock();
                }
                return null;
            } catch (Exception e) {
                Constants.LOG.error("Can't flush", e);
                try {
                    this.state.storage.close();
                } catch (IOException ex) {
                    Constants.LOG.error("Can't stop storage", ex);
                    ex.addSuppressed(e);
                    throw ex;
                }
                throw e;
            }
        });
    }

    @Override
    public void flush() {
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
    public void compact() {
        UtilsClass.State preCompactState = accessState();

        if (preCompactState.memory.isEmpty() && preCompactState.storage.isCompacted()) {
            return;
        }

        Future<Object> future = executor.submit(() -> {
            UtilsClass.State accessState = accessState();

            if (accessState.memory.isEmpty() && accessState.storage.isCompacted()) {
                return null;
            }

            StorageMethods.compact(
                    config,
                    () -> MergeIterator.of(
                            accessState.storage.iterate(VERY_FIRST_KEY,
                                    null
                            ),
                            EntryKeyComparator.INSTANCE
                    )
            );

            Storage storage = StorageMethods.load(config);

            upsertLock.writeLock().lock();
            try {
                this.state = accessState.afterCompact(storage);
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
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private UtilsClass.State accessState() {
        UtilsClass.State accessState = this.state;
        if (accessState.closed) {
            throw new IllegalStateException("Dao is already closed");
        }
        return accessState;
    }

    @Override
    public synchronized void close() throws IOException {
        UtilsClass.State closeState = this.state;
        if (closeState.closed) {
            return;
        }
        executor.shutdown();
        try {
            while (!executor.awaitTermination(10, TimeUnit.DAYS)) {
                Constants.LOG.info("Waiting for termination to close");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        closeState = this.state;
        closeState.storage.close();
        this.state = closeState.afterClosed();
        if (closeState.memory.isEmpty()) {
            return;
        }
        StorageMethods.save(config, closeState.storage, closeState.memory.values());
    }
}
