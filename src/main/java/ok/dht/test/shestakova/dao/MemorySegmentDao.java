package ok.dht.test.shestakova.dao;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.shestakova.dao.base.BaseEntry;
import ok.dht.test.shestakova.dao.base.Config;
import ok.dht.test.shestakova.dao.base.Dao;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MemorySegmentDao implements Dao<MemorySegment, BaseEntry<MemorySegment>> {

    private static final MemorySegment VERY_FIRST_KEY = MemorySegment.ofArray(new byte[]{});

    private volatile State state;

    private final AtomicBoolean isClosed = new AtomicBoolean();

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Config config;

    public MemorySegmentDao(Config config) throws IOException {
        this.config = config;
        this.state = new State(createMemoryStorage(), createMemoryStorage(), Storage.load(config));
        this.isClosed.set(false);
    }

    @Override
    public Iterator<BaseEntry<MemorySegment>> get(MemorySegment from, MemorySegment to) {
        if (isClosed.get()) {
            throw new IllegalStateException("Dao is closed. You can't get entries range");
        }

        State tmpState = getStateUnderReadLock();

        MemorySegment keyFrom = from;
        if (keyFrom == null) {
            keyFrom = VERY_FIRST_KEY;
        }

        return getFromTmpState(keyFrom, to, tmpState);
    }

    private Iterator<BaseEntry<MemorySegment>> getFromTmpState(MemorySegment from, MemorySegment to, State tmpState) {
        Iterator<BaseEntry<MemorySegment>> memoryIterator = getMemoryIterator(from, to, false, tmpState);
        Iterator<BaseEntry<MemorySegment>> flushingMemoryIterator = getMemoryIterator(from, to, true, tmpState);
        Iterator<BaseEntry<MemorySegment>> iterator = tmpState.storage.iterate(from, to);

        return MergeIterator.of(
                List.of(
                        new IndexedPeekIterator<>(0, memoryIterator),
                        new IndexedPeekIterator<>(1, flushingMemoryIterator),
                        new IndexedPeekIterator<>(2, iterator)
                ),
                EntryKeyComparator.INSTANCE
        );
    }

    private Iterator<BaseEntry<MemorySegment>> getMemoryIterator(MemorySegment from, MemorySegment to,
                                                                 boolean isFlushingMemory, State tmpState) {
        if (to == null) {
            return (isFlushingMemory ? tmpState.flushingMemory : tmpState.memory).tailMap(from).values().iterator();
        }
        return (isFlushingMemory ? tmpState.flushingMemory : tmpState.memory).subMap(from, to).values().iterator();
    }

    @Override
    public BaseEntry<MemorySegment> get(MemorySegment key) {
        if (isClosed.get()) {
            throw new IllegalStateException("Dao is closed. You can't get entry");
        }

        State tmpState = getStateUnderReadLock();

        Iterator<BaseEntry<MemorySegment>> iterator = getFromTmpState(key, null, tmpState);
        if (!iterator.hasNext()) {
            return null;
        }
        BaseEntry<MemorySegment> next = iterator.next();
        if (MemorySegmentComparator.INSTANCE.compare(key, next.key()) == 0) {
            return next;
        }
        return null;
    }

    @Override
    public void upsert(BaseEntry<MemorySegment> entry) {
        if (isClosed.get()) {
            throw new IllegalStateException("Dao is closed. You can't upsert entry");
        }

        State tmpState = getStateUnderWriteLock();

        long entrySize = Long.BYTES + entry.key().byteSize() + Long.BYTES + Long.BYTES
                + (entry.value() == null ? 0 : entry.key().byteSize());

        if (tmpState.memorySize.get() + entrySize > config.flushThresholdBytes()) {
            if (!tmpState.flushingMemory.isEmpty()) {
                throw new IllegalStateException("Memory is full. You can't upsert entry");
            }

            lock.writeLock().lock();
            try {
                this.state = new State(createMemoryStorage(), tmpState.memory, tmpState.storage);
                this.state.memory.put(entry.key(), entry);
            } finally {
                lock.writeLock().unlock();
            }

            executorService.execute(this::autoFlush);
            return;
        }

        lock.readLock().lock();
        try {
            this.state.memory.put(entry.key(), entry);
            this.state.memorySize.addAndGet(entrySize);
        } finally {
                lock.readLock().unlock();
            }
    }

    private void autoFlush() {
        State tmpState = getStateUnderWriteLock();

        Storage tmpStorage;
        try {
            flushOperation(tmpState.flushingMemory, tmpState.storage);
            tmpStorage = Storage.load(config);
        } catch (IOException e) {
            throw new RuntimeException("Error during autoFlush", e);
        }

        lock.writeLock().lock();
        try {
            this.state = new State(tmpState.memory, createMemoryStorage(), tmpStorage);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public synchronized void flush() throws IOException {
        if (isClosed.get()) {
            throw new IllegalStateException("Dao is closed. You can't flush");
        }

        State tmpState = getStateUnderWriteLock();

        flushOperation(tmpState.memory, tmpState.storage);

        lock.writeLock().lock();
        try {
            this.state = new State(createMemoryStorage(), tmpState.flushingMemory, Storage.load(config));
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void flushOperation(ConcurrentNavigableMap<MemorySegment, BaseEntry<MemorySegment>> map, Storage storage)
            throws IOException {
        if (storage.isClosed() || map.isEmpty()) {
            return;
        }

        storage.close();
        Path tmp = Storage.save(config, storage, map.values().iterator());

        if (tmp != null) {
            Storage.moveFile(config, tmp, Storage.getFilesCount(config) - 1);
        }
    }

    @Override
    public synchronized void compact() throws IOException {
        if (isClosed.get()) {
            throw new IllegalStateException("Dao is closed. You can't compact");
        }

        State tmpState = getStateUnderWriteLock();

        executorService.execute(() -> {
            try {
                if (tmpState.memory.isEmpty() && Storage.getFilesCount(config) <= 1) {
                    return;
                }

                Iterator<BaseEntry<MemorySegment>> allDataIterator = tmpState.storage.iterate(null, null);
                Path tmp = Storage.save(config, tmpState.storage, allDataIterator);

                if (tmp != null) {
                    Storage.deleteFiles(config);
                    Storage.moveFile(config, tmp, 0);
                }
            } catch (IOException e) {
                throw new RuntimeException("Error during compaction", e);
            }
        });
    }

    @Override
    public synchronized void close() throws IOException {
        if (isClosed.get()) {
            return;
        }

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.DAYS)) {
                throw new RuntimeException("Error during termination");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        State tmpState = getStateUnderWriteLock();

        isClosed.set(true);
        tmpState.storage.close();

        Path tmp = Storage.save(config, tmpState.storage, tmpState.memory.values().iterator());

        if (tmp != null) {
            Storage.moveFile(config, tmp, Storage.getFilesCount(config) - 1);
        }
    }

    private State getStateUnderReadLock() {
        State tmpState;
        lock.readLock().lock();
        try {
            tmpState = this.state;
        } finally {
            lock.readLock().unlock();
        }

        return tmpState;
    }

    private State getStateUnderWriteLock() {
        State tmpState;
        lock.writeLock().lock();
        try {
            tmpState = this.state;
        } finally {
            lock.writeLock().unlock();
        }

        return tmpState;
    }

    private static ConcurrentSkipListMap<MemorySegment, BaseEntry<MemorySegment>> createMemoryStorage() {
        return new ConcurrentSkipListMap<>(MemorySegmentComparator.INSTANCE);
    }
}
