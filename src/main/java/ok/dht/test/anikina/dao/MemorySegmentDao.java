package ok.dht.test.anikina.dao;

import jdk.incubator.foreign.MemorySegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
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

    public MemorySegmentDao(Config config) throws IOException {
        this.config = config;
        this.state = DaoState.newState(config, Storage.load(config));
    }

    @Override
    public Iterator<Entry<MemorySegment>> get(MemorySegment from, MemorySegment to) {
        if (from == null) {
            return getTombstoneFilteringIterator(VERY_FIRST_KEY, to);
        }
        return getTombstoneFilteringIterator(from, to);
    }

    private TombstoneFilteringIterator getTombstoneFilteringIterator(MemorySegment from, MemorySegment to) {
        DaoState daoState = accessState();

        List<Iterator<Entry<MemorySegment>>> iterators = daoState.storage.iterate(from, to);

        iterators.add(daoState.flushing.get(from, to));
        iterators.add(daoState.memory.get(from, to));

        Iterator<Entry<MemorySegment>> mergeIterator = MergeIterator.of(iterators, EntryKeyComparator.INSTANCE);

        return new TombstoneFilteringIterator(mergeIterator);
    }

    @Override
    public Entry<MemorySegment> get(MemorySegment key) {
        DaoState daoState = accessState();

        Entry<MemorySegment> result = daoState.memory.get(key);
        if (result == null) {
            result = daoState.storage.get(key);
        }

        return (result == null || result.isTombstone()) ? null : result;
    }

    @Override
    public void upsert(Entry<MemorySegment> entry) {
        DaoState daoState = accessState();
        boolean runFlush;
        // it is intentionally the read lock!!!
        upsertLock.readLock().lock();
        try {
            runFlush = daoState.memory.put(entry.key(), entry);
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
            DaoState daoState = accessState();
            if (daoState.isFlushing()) {
                if (tolerateFlushInProgress) {
                    return CompletableFuture.completedFuture(null);
                }
                throw new TooManyFlushesInBgException();
            }

            daoState = daoState.prepareForFlush();
            this.state = daoState;
        } finally {
            upsertLock.writeLock().unlock();
        }

        return executor.submit(() -> {
            try {
                DaoState daoState = accessState();

                Storage storage = daoState.storage;
                Storage.save(config, storage, daoState.flushing.values());
                Storage load = Storage.load(config);

                upsertLock.writeLock().lock();
                try {
                    this.state = daoState.afterFlush(load);
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
        DaoState preCompactState = accessState();

        if (preCompactState.memory.isEmpty() && preCompactState.storage.isCompacted()) {
            return;
        }

        Future<Object> future = executor.submit(() -> {
            DaoState daoState = accessState();

            if (daoState.memory.isEmpty() && daoState.storage.isCompacted()) {
                return null;
            }

            Storage.compact(
                    config,
                    () -> MergeIterator.of(
                            daoState.storage.iterate(VERY_FIRST_KEY,
                                    null
                            ),
                            EntryKeyComparator.INSTANCE
                    )
            );

            Storage storage = Storage.load(config);

            upsertLock.writeLock().lock();
            try {
                this.state = daoState.afterCompact(storage);
            } finally {
                upsertLock.writeLock().unlock();
            }

            return null;
        });

        awaitAndUnwrap(future);
    }

    private void awaitAndUnwrap(Future<?> future) throws IOException {
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else if (cause instanceof IOException) {
                throw (IOException) cause;
            } else if (cause instanceof Error) {
                throw (Error) cause;
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    private DaoState accessState() {
        DaoState daoState = this.state;
        if (daoState.closed) {
            throw new IllegalStateException("Dao is already closed");
        }
        return daoState;
    }

    @Override
    public synchronized void close() throws IOException {
        DaoState daoState = this.state;
        if (daoState.closed) {
            return;
        }
        executor.shutdown();
        try {
            while (true) {
                if (executor.awaitTermination(10, TimeUnit.DAYS)) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        daoState = this.state;
        daoState.storage.close();
        this.state = daoState.afterClosed();
        if (daoState.memory.isEmpty()) {
            return;
        }
        Storage.save(config, daoState.storage, daoState.memory.values());
    }

    private static class TombstoneFilteringIterator implements Iterator<Entry<MemorySegment>> {
        private final Iterator<Entry<MemorySegment>> iterator;
        private Entry<MemorySegment> current;

        public TombstoneFilteringIterator(Iterator<Entry<MemorySegment>> iterator) {
            this.iterator = iterator;
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
}
