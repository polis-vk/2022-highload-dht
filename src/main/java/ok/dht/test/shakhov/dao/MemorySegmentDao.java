package ok.dht.test.shakhov.dao;

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
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MemorySegmentDao implements Dao<MemorySegment, Entry<MemorySegment>> {

    private static final Logger LOG = LoggerFactory.getLogger(MemorySegmentDao.class);

    private static final MemorySegment VERY_FIRST_KEY = MemorySegment.ofArray(new byte[] {});

    private final ReadWriteLock upsertLock = new ReentrantReadWriteLock();

    private final Condition noFlushInProgress = upsertLock.writeLock().newCondition();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "MemorySegmentDaoBG"));

    private volatile State state;

    private final DaoConfig daoConfig;

    public MemorySegmentDao(DaoConfig daoConfig) throws IOException {
        this.daoConfig = daoConfig;
        this.state = State.newState(daoConfig, StorageController.load(daoConfig));
    }

    @Override
    public Iterator<Entry<MemorySegment>> get(MemorySegment from, MemorySegment to) {
        if (from == null) {
            return getTombstoneFilteringIterator(VERY_FIRST_KEY, to);
        }
        return getTombstoneFilteringIterator(from, to);
    }

    private TombstoneFilteringIterator getTombstoneFilteringIterator(MemorySegment from, MemorySegment to) {
        State curState = accessState();

        List<Iterator<Entry<MemorySegment>>> iterators = curState.storage.iterate(from, to);

        iterators.add(curState.flushing.get(from, to));
        iterators.add(curState.memory.get(from, to));

        Iterator<Entry<MemorySegment>> mergeIterator = MergeIterator.of(iterators, EntryKeyComparator.INSTANCE);

        return new TombstoneFilteringIterator(mergeIterator);
    }

    @Override
    public Entry<MemorySegment> get(MemorySegment key) {
        State curState = accessState();

        Entry<MemorySegment> result = curState.memory.get(key);

        if (result == null) {
            result = curState.flushing.get(key);
        }

        if (result == null) {
            result = curState.storage.get(key);
        }

        return (result == null || result.isTombstone()) ? null : result;
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void upsert(Entry<MemorySegment> entry) {
        State curState = accessState();

        boolean runFlush;
        // it is intentionally the read lock!!!
        upsertLock.readLock().lock();
        try {
            runFlush = curState.memory.put(entry.key(), entry);
        } finally {
            upsertLock.readLock().unlock();
        }

        if (runFlush) {
            flushInBg(false);
        }
    }

    private Future<?> flushInBg(boolean acceptFlushInProgress) {
        upsertLock.writeLock().lock();
        try {
            State curState = accessState();
            while (curState.isFlushing()) {
                if (acceptFlushInProgress) {
                    return CompletableFuture.completedFuture(null);
                }
                try {
                    noFlushInProgress.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                curState = accessState();
            }

            curState = curState.prepareForFlush();
            this.state = curState;
        } finally {
            upsertLock.writeLock().unlock();
        }

        return executor.submit(() -> {
            try {
                State curState = accessState();

                Storage storage = curState.storage;
                StorageController.save(daoConfig, storage, curState.flushing.values());
                Storage load = StorageController.load(daoConfig);

                upsertLock.writeLock().lock();
                try {
                    this.state = curState.afterFlush(load);
                    noFlushInProgress.signal();
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
            State curState = accessState();
            if (curState.memory.isEmpty() && curState.storage.isCompacted()) {
                return null;
            }

            StorageController.compact(
                    daoConfig,
                    () -> MergeIterator.of(
                            curState.storage.iterate(VERY_FIRST_KEY,
                                    null
                            ),
                            EntryKeyComparator.INSTANCE
                    )
            );

            Storage storage = StorageController.load(daoConfig);

            upsertLock.writeLock().lock();
            try {
                this.state = curState.afterCompact(storage);
            } finally {
                upsertLock.writeLock().unlock();
            }

            curState.storage.maybeClose();
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
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
        }
    }

    private State accessState() {
        State curState = this.state;
        if (curState.closed) {
            throw new IllegalStateException("Dao is already closed");
        }
        return curState;
    }

    @Override
    public synchronized void close() throws IOException {
        State curState = this.state;
        if (curState.closed) {
            return;
        }
        executor.shutdown();
        try {
            while (!executor.awaitTermination(10, TimeUnit.DAYS)) {
                executor.shutdown();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        curState = this.state;
        curState.storage.close();
        this.state = curState.afterClosed();
        if (curState.memory.isEmpty()) {
            return;
        }
        StorageController.save(daoConfig, curState.storage, curState.memory.values());
    }
}
