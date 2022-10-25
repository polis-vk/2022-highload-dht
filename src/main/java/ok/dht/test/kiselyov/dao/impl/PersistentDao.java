package ok.dht.test.kiselyov.dao.impl;

import ok.dht.test.kiselyov.dao.BaseEntry;
import ok.dht.test.kiselyov.dao.Config;
import ok.dht.test.kiselyov.dao.Dao;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class PersistentDao implements Dao<byte[], BaseEntry<byte[]>> {
    private final NavigableMap<byte[], BaseEntry<byte[]>> pairs;
    private final FileOperations fileOperations;

    public PersistentDao(Config config) throws IOException {
        pairs = new ConcurrentSkipListMap<>(Arrays::compare);
        fileOperations = new FileOperations(config);
    }

    @Override
    public Iterator<BaseEntry<byte[]>> get(byte[] from, byte[] to) throws IOException {
        Iterator<BaseEntry<byte[]>> memoryIterator;
        if (from == null && to == null) {
            memoryIterator = pairs.values().iterator();
        } else if (from == null) {
            memoryIterator = pairs.headMap(to).values().iterator();
        } else if (to == null) {
            memoryIterator = pairs.tailMap(from).values().iterator();
        } else {
            memoryIterator = pairs.subMap(from, to).values().iterator();
        }
        Iterator<BaseEntry<byte[]>> diskIterator = fileOperations.diskIterator(from, to);
        Iterator<BaseEntry<byte[]>> mergeIterator = MergeIterator.of(
                List.of(
                        new IndexedPeekIterator(0, memoryIterator),
                        new IndexedPeekIterator(1, diskIterator)
                ),
                EntryKeyComparator.INSTANCE
        );
        return new SkipNullValuesIterator(new IndexedPeekIterator(0, mergeIterator));
    }

    @Override
    public BaseEntry<byte[]> get(byte[] key) throws IOException {
        Iterator<BaseEntry<byte[]>> iterator = get(key, null);
        if (!iterator.hasNext()) {
            return null;
        }
        BaseEntry<byte[]> next = iterator.next();
        if (Arrays.equals(key, next.key())) {
            return next;
        }
        return null;
    }

    @Override
    public void upsert(BaseEntry<byte[]> entry) {
        pairs.put(entry.key(), entry);
    }

    @Override
    public void flush() throws IOException {
        if (pairs.size() == 0) {
            return;
        }
        fileOperations.flush(pairs);
    }

    @Override
    public void compact() throws IOException {
        Iterator<BaseEntry<byte[]>> iterator = get(null, null);
        if (!iterator.hasNext()) {
            return;
        }
        fileOperations.compact(iterator, pairs.size() != 0);
    }

    @Override
    public void close() throws IOException {
        flush();
        fileOperations.clearFileIterators();
        pairs.clear();
    }
}
