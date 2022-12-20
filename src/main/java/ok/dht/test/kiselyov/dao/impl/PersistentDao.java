package ok.dht.test.kiselyov.dao.impl;

import ok.dht.test.kiselyov.dao.BaseEntry;
import ok.dht.test.kiselyov.dao.Config;
import ok.dht.test.kiselyov.dao.Dao;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class PersistentDao implements Dao<byte[], Long, BaseEntry<byte[], Long>> {
    private final NavigableMap<byte[], BaseEntry<byte[], Long>> pairs;
    private final FileOperations fileOperations;

    public PersistentDao(Config config) throws IOException {
        pairs = new ConcurrentSkipListMap<>(Arrays::compare);
        fileOperations = new FileOperations(config);
    }

    @Override
    public Iterator<BaseEntry<byte[], Long>> get(byte[] from, byte[] to) throws IOException {
        Iterator<BaseEntry<byte[], Long>> memoryIterator;
        List<BaseEntry<byte[], Long>> pairValues;
        if (from == null && to == null) {
            pairValues = new ArrayList<>(pairs.values().size());
            pairValues.addAll(pairs.values());
        } else if (from == null) {
            pairValues = new ArrayList<>(pairs.headMap(to).values().size());
            pairValues.addAll(pairs.headMap(to).values());
        } else if (to == null) {
            pairValues = new ArrayList<>(pairs.tailMap(from).values().size());
            pairValues.addAll(pairs.tailMap(from).values());
        } else {
            pairValues = new ArrayList<>(pairs.subMap(from, to).values().size());
            pairValues.addAll(pairs.subMap(from, to).values());
        }
        memoryIterator = pairValues.iterator();
        Iterator<BaseEntry<byte[], Long>> diskIterator = fileOperations.diskIterator(from, to);
        return MergeIterator.of(
                List.of(
                        new IndexedPeekIterator(0, memoryIterator),
                        new IndexedPeekIterator(1, diskIterator)
                ),
                EntryKeyComparator.INSTANCE
        );
    }

    @Override
    public BaseEntry<byte[], Long> get(byte[] key) throws IOException {
        Iterator<BaseEntry<byte[], Long>> iterator = get(key, null);
        if (!iterator.hasNext()) {
            return null;
        }
        BaseEntry<byte[], Long> next = iterator.next();
        if (Arrays.equals(key, next.key())) {
            return next;
        }
        return null;
    }

    @Override
    public void upsert(BaseEntry<byte[], Long> entry) {
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
        Iterator<BaseEntry<byte[], Long>> iterator = get(null, null);
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
