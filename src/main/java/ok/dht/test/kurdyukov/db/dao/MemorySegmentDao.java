package ok.dht.test.kurdyukov.db.dao;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.kurdyukov.db.base.Config;
import ok.dht.test.kurdyukov.db.base.Dao;
import ok.dht.test.kurdyukov.db.base.Entry;
import ok.dht.test.kurdyukov.db.dao.iterator.MergeIterator;
import ok.dht.test.kurdyukov.db.dao.iterator.TombstoneFilteringIterator;
import ok.dht.test.kurdyukov.db.dao.storage.Storage;
import ok.dht.test.kurdyukov.db.dao.storage.StorageUtils;
import ok.dht.test.kurdyukov.db.exception.TooManyFlushesInBgException;
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

    private volatile State stateDao;
    private final Config config;

    public MemorySegmentDao(Config config) throws IOException {
        this.config = config;
        this.stateDao = State.newState(config, StorageUtils.load(config));
    }

    @Override
    public Iterator<Entry<MemorySegment>> get(MemorySegment from, MemorySegment to) {
        return getTombstoneFilteringIterator(Objects.requireNonNullElse(from, VERY_FIRST_KEY), to);
    }

    private TombstoneFilteringIterator getTombstoneFilteringIterator(MemorySegment from, MemorySegment to) {
        State currentStateDao = accessState();

        List<Iterator<Entry<MemorySegment>>> iterators = currentStateDao.storage.iterate(from, to);

        iterators.add(currentStateDao.flushing.get(from, to));
        iterators.add(currentStateDao.memory.get(from, to));

        Iterator<Entry<MemorySegment>> mergeIterator = MergeIterator.of(iterators, EntryKeyComparator.INSTANCE);

        return new TombstoneFilteringIterator(mergeIterator);
    }

    @Override
    public Entry<MemorySegment> get(MemorySegment key) {
        State currentStateDao = accessState();

        Entry<MemorySegment> result = currentStateDao.memory.get(key);
        if (result == null) {
            result = currentStateDao.storage.get(key);
        }

        return (result == null || result.isTombstone()) ? null : result;
    }

    @Override
    public void upsert(Entry<MemorySegment> entry) {
        State currentStateDao = accessState();

        boolean runFlush;
        upsertLock.readLock().lock();
        try {
            runFlush = currentStateDao.memory.put(entry.key(), entry);
        } finally {
            upsertLock.readLock().unlock();
        }

        if (runFlush) {
            flushInBg(false).isCancelled();
        }
    }

    private Future<?> flushInBg(boolean tolerateFlushInProgress) {
        upsertLock.writeLock().lock();
        try {
            State currentStateDao = accessState();
            if (currentStateDao.isFlushing()) {
                if (tolerateFlushInProgress) {
                    return CompletableFuture.completedFuture(null);
                }
                throw new TooManyFlushesInBgException();
            }

            currentStateDao = currentStateDao.prepareForFlush();
            this.stateDao = currentStateDao;
        } finally {
            upsertLock.writeLock().unlock();
        }

        return executor.submit(() -> {
            try {
                State currentStateDao = accessState();

                Storage storage = currentStateDao.storage;
                StorageUtils.save(config, storage, currentStateDao.flushing.values());
                Storage load = StorageUtils.load(config);

                upsertLock.writeLock().lock();
                try {
                    this.stateDao = currentStateDao.afterFlush(load);
                } finally {
                    upsertLock.writeLock().unlock();
                }

                return null;
            } catch (Exception e) {
                LOG.error("Can't flush", e);
                try {
                    this.stateDao.storage.close();
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
            runFlush = stateDao.memory.overflow();
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
            State currentStateDao = accessState();

            if (currentStateDao.memory.isEmpty() && currentStateDao.storage.isCompacted()) {
                return null;
            }

            StorageUtils.compact(
                    config,
                    () -> MergeIterator.of(
                            currentStateDao.storage.iterate(VERY_FIRST_KEY,
                                    null
                            ),
                            EntryKeyComparator.INSTANCE
                    )
            );

            Storage storage = StorageUtils.load(config);

            upsertLock.writeLock().lock();
            try {
                this.stateDao = currentStateDao.afterCompact(storage);
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

    private State accessState() {
        State currentStateDao = this.stateDao;
        if (currentStateDao.closed) {
            throw new IllegalStateException("Dao is already closed");
        }
        return currentStateDao;
    }

    @Override
    public synchronized void close() throws IOException {
        State currentStateDao = this.stateDao;
        if (currentStateDao.closed) {
            return;
        }
        executor.shutdown();
        try {
            while (true) {
                if (executor.awaitTermination(10, TimeUnit.DAYS))
                    break;
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        currentStateDao = this.stateDao;
        currentStateDao.storage.close();
        this.stateDao = currentStateDao.afterClosed();
        if (currentStateDao.memory.isEmpty()) {
            return;
        }
        StorageUtils.save(config, currentStateDao.storage, currentStateDao.memory.values());
    }
}
