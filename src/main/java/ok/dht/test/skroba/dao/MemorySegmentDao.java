package ok.dht.test.skroba.dao;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.skroba.dao.base.Config;
import ok.dht.test.skroba.dao.base.Dao;
import ok.dht.test.skroba.dao.base.Entry;
import ok.dht.test.skroba.dao.comparators.EntryKeyComparator;
import ok.dht.test.skroba.dao.exceptions.TooManyFlushesInBgException;
import ok.dht.test.skroba.dao.iterators.MergeIterator;
import ok.dht.test.skroba.dao.iterators.TombstoneFilteringIterator;
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
    private final Config config;
    private volatile State stateOfClass;
    
    public MemorySegmentDao(Config config) throws IOException {
        this.config = config;
        this.stateOfClass = State.newState(config, StorageUtils.load(config));
    }
    
    @Override
    public Iterator<Entry<MemorySegment>> get(MemorySegment from, MemorySegment to) {
        return getTombstoneFilteringIterator(from == null ? VERY_FIRST_KEY : from, to);
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
        upsertLock.writeLock().lock();
        try {
            State state = accessState();
            if (state.isFlushing()) {
                if (tolerateFlushInProgress) {
                    // or any other completed future
                    return CompletableFuture.completedFuture(null);
                }
                throw new TooManyFlushesInBgException();
            }
            
            state = state.prepareForFlush();
            this.stateOfClass = state;
        } finally {
            upsertLock.writeLock().unlock();
        }
        
        return executor.submit(() -> {
            try {
                State state = accessState();
                
                Storage storage = state.storage;
                StorageUtils.save(config, storage, state.flushing.values());
                Storage load = StorageUtils.load(config);
                
                upsertLock.writeLock().lock();
                try {
                    this.stateOfClass = state.afterFlush(load);
                } finally {
                    upsertLock.writeLock().unlock();
                }
                storage.maybeClose();
                return null;
            } catch (Exception e) {
                LOG.error("Can't flush", e);
                try {
                    this.stateOfClass.storage.close();
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
            runFlush = stateOfClass.memory.overflow();
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
            
            StorageUtils.compact(
                    config,
                    () -> MergeIterator.of(
                            state.storage.iterate(VERY_FIRST_KEY,
                                    null
                            ),
                            EntryKeyComparator.INSTANCE
                    )
            );
            
            Storage storage = StorageUtils.load(config);
            
            upsertLock.writeLock().lock();
            try {
                this.stateOfClass = state.afterCompact(storage);
            } finally {
                upsertLock.writeLock().unlock();
            }
            
            state.storage.maybeClose();
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
        State state = this.stateOfClass;
        if (state.closed) {
            throw new IllegalStateException("Dao is already closed");
        }
        return state;
    }
    
    @Override
    public synchronized void close() throws IOException {
        State state = this.stateOfClass;
        if (state.closed) {
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
        state = this.stateOfClass;
        state.storage.close();
        this.stateOfClass = state.afterClosed();
        if (state.memory.isEmpty()) {
            return;
        }
        StorageUtils.save(config, state.storage, state.memory.values());
    }
}
