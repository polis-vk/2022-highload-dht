package ok.dht.test.labazov.dao;

import jdk.incubator.foreign.MemorySegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOG = LoggerFactory.getLogger(MemorySegmentDao.class);

    private static final MemorySegment VERY_FIRST_KEY = MemorySegment.ofArray(new byte[]{});

    private final ReadWriteLock upsertLock = new ReentrantReadWriteLock();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "MemorySegmentDaoBG"));

    private volatile MemorySegmentDaoState state;

    private final Config config;

    public MemorySegmentDao(Config config) throws IOException {
        this.config = config;
        this.state = MemorySegmentDaoState.newState(config, StorageUtils.load(config));
    }

    @Override
    public Iterator<Entry<MemorySegment>> get(MemorySegment from, MemorySegment to) {
        return getTombstoneFilteringIterator(from == null ? VERY_FIRST_KEY : from, to);
    }

    private TombstoneFilteringIterator getTombstoneFilteringIterator(MemorySegment from, MemorySegment to) {
        MemorySegmentDaoState st = accessState();

        List<Iterator<Entry<MemorySegment>>> iterators = st.storage.iterate(from, to);

        iterators.add(st.flushing.get(from, to));
        iterators.add(st.memory.get(from, to));

        Iterator<Entry<MemorySegment>> mergeIterator = MergeIterator.of(iterators, EntryKeyComparator.INSTANCE);

        return new TombstoneFilteringIterator(mergeIterator);
    }

    @Override
    public Entry<MemorySegment> get(MemorySegment key) {
        MemorySegmentDaoState st = accessState();

        Entry<MemorySegment> result = st.memory.get(key);
        if (result == null) {
            result = st.storage.get(key);
        }

        return (result == null || result.isTombstone()) ? null : result;
    }

    @Override
    public void upsert(Entry<MemorySegment> entry) {
        MemorySegmentDaoState st = accessState();

        boolean runFlush;
        // it is intentionally the read lock!!!
        upsertLock.readLock().lock();
        try {
            runFlush = st.memory.put(entry.key(), entry);
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
            MemorySegmentDaoState st = accessState();
            if (st.isFlushing()) {
                if (tolerateFlushInProgress) {
                    // or any other completed future
                    return CompletableFuture.completedFuture(null);
                }
                throw new TooManyFlushesInBgException();
            }

            st = st.prepareForFlush();
            this.state = st;
        } finally {
            upsertLock.writeLock().unlock();
        }

        return executor.submit(() -> {
            try {
                MemorySegmentDaoState st = accessState();

                Storage storage = st.storage;
                StorageUtils.save(config, storage, st.flushing.values());
                Storage load = StorageUtils.load(config);

                upsertLock.writeLock().lock();
                try {
                    this.state = st.afterFlush(load);
                } finally {
                    upsertLock.writeLock().unlock();
                }
                // Empty
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
        MemorySegmentDaoState preCompactState = accessState();

        if (preCompactState.memory.isEmpty() && preCompactState.storage.isCompacted()) {
            return;
        }

        Future<Object> future = executor.submit(() -> {
            MemorySegmentDaoState st = accessState();

            if (st.memory.isEmpty() && st.storage.isCompacted()) {
                return null;
            }

            Storage.compact(
                    config,
                    () -> MergeIterator.of(
                            st.storage.iterate(VERY_FIRST_KEY,
                                    null
                            ),
                            EntryKeyComparator.INSTANCE
                    )
            );

            Storage storage = StorageUtils.load(config);

            upsertLock.writeLock().lock();
            try {
                this.state = st.afterCompact(storage);
            } finally {
                upsertLock.writeLock().unlock();
            }

            // Empty
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
            throw new RuntimeException(e);
        }
    }

    private MemorySegmentDaoState accessState() {
        MemorySegmentDaoState st = this.state;
        if (st.closed) {
            throw new IllegalStateException("Dao is already closed");
        }
        return st;
    }

    @Override
    public synchronized void close() throws IOException {
        MemorySegmentDaoState st = this.state;
        if (st.closed) {
            return;
        }
        executor.shutdown();
        try {
            while (0 < 1) {
                if (executor.awaitTermination(10, TimeUnit.DAYS)) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        st = this.state;
        st.storage.close();
        this.state = st.afterClosed();
        if (st.memory.isEmpty()) {
            return;
        }
        StorageUtils.save(config, st.storage, st.memory.values());
    }

}
