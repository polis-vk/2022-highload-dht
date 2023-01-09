package ok.dht.test.panov.dao.lsm;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.panov.dao.Config;
import ok.dht.test.panov.dao.Dao;
import ok.dht.test.panov.dao.Entry;
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

    private volatile State state;

    private final Config config;

    public MemorySegmentDao(Config config) throws IOException {
        this.config = config;
        this.state = State.newState(config, StorageCompanionObject.load(config));
    }

    @Override
    public Iterator<Entry<MemorySegment>> get(MemorySegment from, MemorySegment to) {
        return getIterator(from == null ? VERY_FIRST_KEY : from, to);
    }

    private Iterator<Entry<MemorySegment>> getIterator(MemorySegment from, MemorySegment to) {
        State curState = accessState();

        List<Iterator<Entry<MemorySegment>>> iterators = curState.storage.iterate(from, to);

        iterators.add(curState.flushing.get(from, to));
        iterators.add(curState.memory.get(from, to));

        return MergeIterator.of(iterators, EntryKeyComparator.INSTANCE);
    }

    @Override
    public Entry<MemorySegment> get(MemorySegment key) {
        State curState = accessState();

        Entry<MemorySegment> result = curState.memory.get(key);
        if (result == null) {
            result = curState.storage.get(key);
        }

        return result;
    }

    @Override
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

    private Future<?> flushInBg(boolean tolerateFlushInProgress) {
        upsertLock.writeLock().lock();
        try {
            State curState = accessState();
            if (curState.isFlushing()) {
                if (tolerateFlushInProgress) {
                    // or any other completed future
                    return CompletableFuture.completedFuture(null);
                }
                throw new TooManyFlushesInBgException();
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
                StorageCompanionObject.save(config, storage, curState.flushing.values());
                Storage load = StorageCompanionObject.load(config);

                upsertLock.writeLock().lock();
                try {
                    this.state = curState.afterFlush(load);
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
            State curState = accessState();

            if (curState.memory.isEmpty() && curState.storage.isCompacted()) {
                return null;
            }

            StorageCompanionObject.compact(
                    config,
                    () -> MergeIterator.of(
                            curState.storage.iterate(VERY_FIRST_KEY,
                                    null
                            ),
                            EntryKeyComparator.INSTANCE
                    )
            );

            Storage storage = StorageCompanionObject.load(config);

            upsertLock.writeLock().lock();
            try {
                this.state = curState.afterCompact(storage);
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
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
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
            while (true) {
                if (executor.awaitTermination(10, TimeUnit.DAYS)) {
                    break;
                }
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
        StorageCompanionObject.save(config, curState.storage, curState.memory.values());
    }
}
