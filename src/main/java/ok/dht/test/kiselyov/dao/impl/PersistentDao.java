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

public class PersistentDao implements Dao<byte[], BaseEntry<byte[]>> {
    private final NavigableMap<byte[], BaseEntry<byte[]>> pairs;
    private final FileOperations fileOperations;

    public PersistentDao(Config config) throws IOException {
        pairs = new ConcurrentSkipListMap<>(Arrays::compare);
        fileOperations = new FileOperations(config);
    }

    @Override
    public Iterator<EntryWithTimestamp> get(byte[] from, byte[] to) throws IOException {
        Iterator<EntryWithTimestamp> memoryIterator;
        List<EntryWithTimestamp> pairValues;
        if (from == null && to == null) {
            pairValues = new ArrayList<>(pairs.values().size());
            for (BaseEntry<byte[]> pair : pairs.values()) {
                pairValues.add(new EntryWithTimestamp(pair, System.currentTimeMillis()));
            }
        } else if (from == null) {
            pairValues = new ArrayList<>(pairs.headMap(to).values().size());
            for (BaseEntry<byte[]> pair : pairs.headMap(to).values()) {
                pairValues.add(new EntryWithTimestamp(pair, System.currentTimeMillis()));
            }
        } else if (to == null) {
            pairValues = new ArrayList<>(pairs.tailMap(from).values().size());
            for (BaseEntry<byte[]> pair : pairs.tailMap(from).values()) {
                pairValues.add(new EntryWithTimestamp(pair, System.currentTimeMillis()));
            }
        } else {
            pairValues = new ArrayList<>(pairs.subMap(from, to).values().size());
            for (BaseEntry<byte[]> pair : pairs.subMap(from, to).values()) {
                pairValues.add(new EntryWithTimestamp(pair, System.currentTimeMillis()));
            }
        }
        memoryIterator = pairValues.iterator();
        Iterator<EntryWithTimestamp> diskIterator = fileOperations.diskIterator(from, to);
        Iterator<EntryWithTimestamp> mergeIterator = MergeIterator.of(
                List.of(
                        new IndexedPeekIterator(0, memoryIterator),
                        new IndexedPeekIterator(1, diskIterator)
                ),
                EntryKeyComparator.INSTANCE
        );
        return new SkipNullValuesIterator(new IndexedPeekIterator(0, mergeIterator));
    }

    @Override
    public EntryWithTimestamp get(byte[] key) throws IOException {
        Iterator<EntryWithTimestamp> iterator = get(key, null);
        if (!iterator.hasNext()) {
            return null;
        }
        EntryWithTimestamp next = iterator.next();
        if (Arrays.equals(key, next.getEntry().key())) {
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
        Iterator<EntryWithTimestamp> iterator = get(null, null);
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
