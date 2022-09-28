package ok.dht.test.dergunov.database;

import jdk.incubator.foreign.MemorySegment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MemorySegmentDao implements Dao<MemorySegment, Entry<MemorySegment>> {

    private static final MemorySegment VERY_FIRST_KEY = MemorySegment.ofArray(new byte[]{});
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Config config;
    private ConcurrentNavigableMap<MemorySegment, Entry<MemorySegment>> memory = createMemoryStorage();
    // FIXME make it final
    private Storage storage;

    public MemorySegmentDao(Config config) throws IOException {
        this.config = config;
        this.storage = Storage.load(config);
    }

    private static ConcurrentSkipListMap<MemorySegment, Entry<MemorySegment>> createMemoryStorage() {
        return new ConcurrentSkipListMap<>(MemorySegmentComparator.INSTANCE);
    }

    @Override
    public Iterator<Entry<MemorySegment>> get(MemorySegment from, MemorySegment to) {
        lock.readLock().lock();
        try {
            if (from == null) {
                from = VERY_FIRST_KEY;
            }

            ArrayList<Iterator<Entry<MemorySegment>>> iterators = storage.iterate(from, to);
            iterators.add(getMemoryIterator(from, to));

            Iterator<Entry<MemorySegment>> mergeIterator = MergeIterator.of(iterators, EntryKeyComparator.INSTANCE);

            return new TombstoneFilteringIterator(mergeIterator);
        } finally {
            lock.readLock().unlock();
        }
    }

    private Iterator<Entry<MemorySegment>> getMemoryIterator(MemorySegment from, MemorySegment to) {
        lock.readLock().lock();
        try {

            if (to == null) {
                return memory.tailMap(from).values().iterator();
            }

            return memory.subMap(from, to).values().iterator();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Entry<MemorySegment> get(MemorySegment key) {
        lock.readLock().lock();
        try {
            Entry<MemorySegment> result = memory.get(key);
            if (result == null) {
                result = storage.get(key);
            }

            return (result == null || result.isTombstone()) ? null : result;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void upsert(Entry<MemorySegment> entry) {
        lock.readLock().lock();
        try {
            memory.put(entry.key(), entry);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void flush() throws IOException {
        lock.writeLock().lock();
        try {
            if (storage.isClosed()) {
                return;
            }
            if (memory.isEmpty()) {
                return;
            }
            storage.close();
            Storage.save(config, storage, memory.values());
            memory = createMemoryStorage();
            this.storage = Storage.load(config);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void compact() throws IOException {
        lock.writeLock().lock();
        try {
            if (memory.isEmpty() && storage.isCompacted()) {
                return;
            }
            Storage.compact(config, this::all);
            storage.close();
            memory = createMemoryStorage();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void close() throws IOException {
        lock.writeLock().lock();
        try {
            if (storage.isClosed()) {
                return;
            }
            storage.close();
            if (memory.isEmpty()) {
                return;
            }
            Storage.save(config, storage, memory.values());
        } finally {
            lock.writeLock().unlock();
        }
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
