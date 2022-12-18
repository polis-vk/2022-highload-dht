package ok.dht.test.kuleshov.dao.storage;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.kuleshov.dao.Config;
import ok.dht.test.kuleshov.dao.Dao;
import ok.dht.test.kuleshov.dao.Entry;
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

    private volatile State daoState;

    private final Config config;

    public MemorySegmentDao(Config config) throws IOException {
        this.config = config;
        this.daoState = State.newState(config, Storage.load(config));
    }

    @Override
    public Iterator<Entry<MemorySegment>> get(MemorySegment fromSegment, MemorySegment toSegment) {
        MemorySegment fromSegmentFinal = fromSegment;
        if (fromSegment == null) {
            fromSegmentFinal = VERY_FIRST_KEY;
        }

        return getTombstoneFilteringIterator(fromSegmentFinal, toSegment);
    }

    private TombstoneFilteringIterator getTombstoneFilteringIterator(MemorySegment from, MemorySegment to) {
        State state = accessState();

        List<Iterator<Entry<MemorySegment>>> iterators = state.storage.iterate(from, to);

        iterators.add(state.flushing.get(from, to));
        iterators.add(state.memory.get(from, to));

        Iterator<Entry<MemorySegment>> mergeIterator = MergeIterator.of(iterators, EntryKeyComparator.INSTANCE);

        return new TombstoneFilteringIterator(mergeIterator);
    }

    @Override
    public Entry<MemorySegment> get(MemorySegment key) {
        State state = accessState();

        Entry<MemorySegment> result = state.memory.get(key);
        if (result == null) {
            result = state.storage.get(key);
        }

        return (result == null || result.isTombstone()) ? null : result;
    }

    @Override
    public void upsert(Entry<MemorySegment> entry) {
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
                this.daoState = freezedState;
                break;
            } finally {
                upsertLock.writeLock().unlock();
            }
        }

        return executor.submit(() -> {
            try {
                State state = accessState();

                Storage storage = state.storage;
                Storage.save(config, storage, state.flushing.values());
                Storage load = Storage.load(config);

                upsertLock.writeLock().lock();
                try {
                    this.daoState = state.afterFlush(load);
                } finally {
                    upsertLock.writeLock().unlock();
                }
                storage.maybeClose();
                return null;
            } catch (Exception e) {
                LOG.error("Can't flush", e);
                try {
                    this.daoState.storage.close();
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
    public void flush() {
        boolean runFlush;
        // it is intentionally the read lock!!!
        upsertLock.writeLock().lock();
        try {
            runFlush = daoState.memory.overflow();
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
                this.daoState = state.afterCompact(storage);
            } finally {
                upsertLock.writeLock().unlock();
            }

            state.storage.maybeClose();
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

    private State accessState() {
        State state = this.daoState;
        if (state.closed) {
            throw new IllegalStateException("Dao is already closed");
        }
        return state;
    }

    @Override
    public synchronized void close() throws IOException {
        State accessState = daoState;
        if (accessState.closed) {
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
        accessState = daoState;
        accessState.storage.close();
        daoState = accessState.afterClosed();
        if (accessState.memory.isEmpty()) {
            return;
        }
        Storage.save(config, daoState.storage, daoState.memory.values());
    }
}
