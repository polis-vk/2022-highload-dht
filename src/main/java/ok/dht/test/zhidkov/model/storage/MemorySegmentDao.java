package ok.dht.test.zhidkov.model.storage;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.zhidkov.model.comparators.EntryKeyComparator;
import ok.dht.test.zhidkov.model.exceptions.TooManyFlushesInBgException;
import ok.dht.test.zhidkov.model.iterators.MergeIterator;
import ok.dht.test.zhidkov.model.iterators.TombstoneFilteringIterator;
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
        this.state = State.newState(config, Storage.load(config));
    }

    @Override
    public Iterator<Entry<MemorySegment>> get(MemorySegment from, MemorySegment to) {
        return from == null
                ? getTombstoneFilteringIterator(VERY_FIRST_KEY, to)
                : getTombstoneFilteringIterator(from, to);
    }

    private TombstoneFilteringIterator getTombstoneFilteringIterator(MemorySegment from, MemorySegment to) {
        State currentState = accessState();

        List<Iterator<Entry<MemorySegment>>> iterators = currentState.storage.iterate(from, to);

        iterators.add(currentState.flushing.get(from, to));
        iterators.add(currentState.memory.get(from, to));

        Iterator<Entry<MemorySegment>> mergeIterator = MergeIterator.of(iterators, EntryKeyComparator.INSTANCE);

        return new TombstoneFilteringIterator(mergeIterator);
    }

    @Override
    public Entry<MemorySegment> get(MemorySegment key) {
        State currentState = accessState();

        Entry<MemorySegment> result = currentState.memory.get(key);
        if (result == null) {
            result = currentState.storage.get(key);
        }

        return (result == null || result.isTombstone()) ? null : result;
    }

    @Override
    public void upsert(Entry<MemorySegment> entry) {
        State currentState = accessState();

        boolean runFlush;
        // it is intentionally the read lock!!!
        upsertLock.readLock().lock();
        try {
            runFlush = currentState.memory.put(entry.key(), entry);
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
            State currentState = accessState();
            if (currentState.isFlushing()) {
                if (tolerateFlushInProgress) {
                    // or any other completed future
                    return CompletableFuture.completedFuture(null);
                }
                throw new TooManyFlushesInBgException();
            }

            currentState = currentState.prepareForFlush();
            this.state = currentState;
        } finally {
            upsertLock.writeLock().unlock();
        }

        return executor.submit(() -> {
            try {
                State currentState = accessState();

                Storage storage = currentState.storage;
                Storage.save(config, storage, currentState.flushing.values());
                Storage load = Storage.load(config);

                upsertLock.writeLock().lock();
                try {
                    this.state = currentState.afterFlush(load);
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
            State currentState = accessState();

            if (currentState.memory.isEmpty() && currentState.storage.isCompacted()) {
                return null;
            }

            Storage.compact(
                    config,
                    () -> MergeIterator.of(
                            currentState.storage.iterate(VERY_FIRST_KEY,
                                    null
                            ),
                            EntryKeyComparator.INSTANCE
                    )
            );

            Storage storage = Storage.load(config);

            upsertLock.writeLock().lock();
            try {
                this.state = currentState.afterCompact(storage);
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
            Throwable exceptionCause = e.getCause();
           if (exceptionCause instanceof IOException) {
                throw (IOException) exceptionCause;
            } else if (exceptionCause instanceof Error) {
                throw (Error) exceptionCause;
            } else {
                throw (RuntimeException) exceptionCause;
            }
        }
    }

    private State accessState() {
        State currentState = this.state;
        if (currentState.closed) {
            throw new IllegalStateException("Dao is already closed");
        }
        return currentState;
    }

    @Override
    public synchronized void close() throws IOException {
        State currentState = this.state;
        if (currentState.closed) {
            return;
        }
        executor.shutdown();
        boolean isTerminated = false;
        while (!isTerminated) {
            try {
                isTerminated = executor.awaitTermination(10, TimeUnit.DAYS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        currentState = this.state;
        currentState.storage.close();
        this.state = currentState.afterClosed();
        if (currentState.memory.isEmpty()) {
            return;
        }
        Storage.save(config, currentState.storage, currentState.memory.values());
    }
}
