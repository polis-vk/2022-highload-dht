package ok.dht.test.kosnitskiy.dao;

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

public class MemorySegmentDao {

    private static final Logger LOG = LoggerFactory.getLogger(MemorySegmentDao.class);

    private static final MemorySegment VERY_FIRST_KEY = MemorySegment.ofArray(new byte[]{});

    private final ReadWriteLock upsertLock = new ReentrantReadWriteLock();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "MemorySegmentDaoBG"));

    private volatile State state;

    private final Config config;

    public MemorySegmentDao(Config config) throws IOException {
        this.config = config;
        this.state = State.newState(config, StorageUtils.load(config));
    }

    public Iterator<Entry<MemorySegment>> get(MemorySegment from, MemorySegment to) {
        MemorySegment localFrom;
        if (from == null) {
            localFrom = VERY_FIRST_KEY;
        } else {
            localFrom = from;
        }

        return getTombstoneFilteringIterator(localFrom, to);
    }

    private TombstoneFilteringIterator getTombstoneFilteringIterator(MemorySegment from, MemorySegment to) {
        State accState = accessState();

        List<Iterator<Entry<MemorySegment>>> iterators = accState.storage.iterate(from, to);

        iterators.add(accState.flushing.get(from, to));
        iterators.add(accState.memory.get(from, to));

        Iterator<Entry<MemorySegment>> mergeIterator = MergeIterator.of(iterators, EntryKeyComparator.INSTANCE);

        return new TombstoneFilteringIterator(mergeIterator);
    }

    public Entry<MemorySegment> get(MemorySegment key) {
        State accState = accessState();

        Entry<MemorySegment> result = accState.memory.get(key);
        if (result == null) {
            result = accState.storage.get(key);
        }

        return (result == null || result.isTombstone()) ? null : result;
    }

    public void upsert(Entry<MemorySegment> entry) {
        State accState = accessState();

        boolean runFlush;
        // it is intentionally the read lock!!!
        upsertLock.readLock().lock();
        try {
            runFlush = accState.memory.put(entry.key(), entry);
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
            State accState = accessState();
            if (accState.isFlushing()) {
                if (tolerateFlushInProgress) {
                    // or any other completed future
                    return CompletableFuture.completedFuture(null);
                }
                throw new TooManyFlushesInBgException();
            }

            accState = accState.prepareForFlush();
            this.state = accState;
        } finally {
            upsertLock.writeLock().unlock();
        }

        return executor.submit(() -> {
            try {
                State accState = accessState();

                Storage storage = accState.storage;
                StorageUtils.save(config, storage, accState.flushing.values());
                Storage load = StorageUtils.load(config);

                upsertLock.writeLock().lock();
                try {
                    this.state = accState.afterFlush(load);
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

    public void compact() throws IOException {
        State preCompactState = accessState();

        if (preCompactState.memory.isEmpty() && preCompactState.storage.isCompacted()) {
            return;
        }

        Future<Object> future = executor.submit(() -> {
            State accState = accessState();

            if (accState.memory.isEmpty() && accState.storage.isCompacted()) {
                return null;
            }

            StorageUtils.compact(
                    config,
                    () -> MergeIterator.of(
                            accState.storage.iterate(VERY_FIRST_KEY,
                                    null
                            ),
                            EntryKeyComparator.INSTANCE
                    )
            );

            Storage storage = StorageUtils.load(config);

            upsertLock.writeLock().lock();
            try {
                this.state = accState.afterCompact(storage);
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
        } catch (ExecutionException | InterruptedException e) {
            LOG.error(e.getMessage());
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
        }
    }

    private State accessState() {
        State accState = this.state;
        if (accState.closed) {
            throw new IllegalStateException("Dao is already closed");
        }
        return accState;
    }

    public synchronized void close() throws IOException {
        State accState = this.state;
        if (accState.closed) {
            return;
        }
        executor.shutdown();
        try {
            //noinspection StatementWithEmptyBody
            while (!executor.awaitTermination(10, TimeUnit.DAYS)) {
                LOG.info("waiting termination");
            }
        } catch (InterruptedException ie) {
            LOG.error("InterruptedException: ", ie);
            Thread.currentThread().interrupt();
        }
        accState = this.state;
        accState.storage.close();
        this.state = accState.afterClosed();
        if (accState.memory.isEmpty()) {
            return;
        }
        StorageUtils.save(config, accState.storage, accState.memory.values());
    }

}
