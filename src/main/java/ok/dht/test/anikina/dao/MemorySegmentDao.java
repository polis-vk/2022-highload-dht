package ok.dht.test.anikina.dao;

import jdk.incubator.foreign.MemorySegment;

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
        while (true) {
            upsertLock.writeLock().lock();
            try {
                DaoState daoState = accessState();
                if (daoState.isFlushing()) {
                    if (tolerateFlushInProgress) {
                        // or any other completed future
                        return CompletableFuture.completedFuture(null);
                    }
                    continue;
                }

                daoState = daoState.prepareForFlush();
                this.state = daoState;
            } finally {
                upsertLock.writeLock().unlock();
            }
        }
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

    private void awaitAndUnwrap(Future<?> future) {
        try {
            future.get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
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
        DaoState state = this.state;
        if (state.closed) {
            return;
        }
        executor.shutdown();
        try {
            //noinspection StatementWithEmptyBody
            while (!executor.awaitTermination(10, TimeUnit.DAYS)) {}
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        state = this.state;
        state.storage.close();
        this.state = state.afterClosed();
        if (state.memory.isEmpty()) {
            return;
        }
        Storage.save(config, state.storage, state.memory.values());
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
