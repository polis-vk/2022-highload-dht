package ok.dht.test.lutsenko.dao;

import ok.dht.test.lutsenko.dao.common.BaseEntry;

import java.util.Collections;
import java.util.Iterator;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class MemStorage {

    private volatile MemTable firstTable = new MemTable();
    private volatile MemTable secondTable = new MemTable();
    private final long tableMaxBytesSize;

    public MemStorage(long tableMaxBytesSize) {
        this.tableMaxBytesSize = tableMaxBytesSize;
    }

    public void upsertToSecondTable(BaseEntry<String> entry, int entryBytes) {
        if (secondTable.getBytes().addAndGet(entryBytes) < tableMaxBytesSize) {
            secondTable.put(entry);
        } else {
            rejectUpsert();
        }
    }

    public AtomicLong firstTableBytes() {
        return firstTable.getBytes();
    }

    public AtomicBoolean firstTableOnFlush() {
        return firstTable.onFlush();
    }

    public void putFirstTable(BaseEntry<String> entry) {
        firstTable.put(entry);
    }

    public boolean firstTableNotOnFlushAndSetTrue() {
        return !firstTable.onFlush().getAndSet(true);
    }

    public void clearFirstTable() {
        firstTable = secondTable;
        secondTable = new MemTable();
    }

    public boolean isEmpty() {
        return firstTable.isEmpty() && secondTable.isEmpty();
    }

    public void rejectUpsert() {
        throw new RuntimeException("Can`t upsert now, try later");
    }

    public Iterator<BaseEntry<String>> firstTableIterator(String from, String to) {
        return firstTable.iterator(from, to);
    }

    public Iterator<BaseEntry<String>> secondTableIterator(String from, String to) {
        return secondTable.iterator(from, to);
    }

    public Iterator<BaseEntry<String>> iterator(String from, String to) {
        Iterator<BaseEntry<String>> firstTableIterator = firstTable.iterator(from, to);
        Iterator<BaseEntry<String>> secondTableIterator = secondTable.iterator(from, to);
        if (!firstTableIterator.hasNext() && !secondTableIterator.hasNext()) {
            return Collections.emptyIterator();
        }
        if (!firstTableIterator.hasNext()) {
            return secondTableIterator(from, to);
        }
        if (!secondTableIterator.hasNext()) {
            return firstTableIterator(from, to);
        }
        return new Iterator<>() {

            private BaseEntry<String> firstTableEntry = firstTableIterator.next();
            private BaseEntry<String> secondTableEntry = secondTableIterator.next();
            private String firstTableLastReadKey = firstTableEntry.key();
            private String secondTableLastReadKey = secondTableEntry.key();
            private final NavigableMap<String, BaseEntry<String>> tempData = mapWithTwoEntries(
                    firstTableEntry,
                    secondTableEntry
            );

            @Override
            public boolean hasNext() {
                return !tempData.isEmpty();
            }

            @Override
            public BaseEntry<String> next() {
                BaseEntry<String> removed = tempData.pollFirstEntry().getValue();
                if (removed.key().equals(firstTableLastReadKey) && firstTableIterator.hasNext()) {
                    firstTableEntry = firstTableIterator.next();
                    tempData.put(firstTableEntry.key(), firstTableEntry);
                    firstTableLastReadKey = firstTableEntry.key();
                }
                if (removed.key().equals(secondTableLastReadKey) && secondTableIterator.hasNext()) {
                    secondTableEntry = secondTableIterator.next();
                    tempData.put(secondTableEntry.key(), secondTableEntry);
                    secondTableLastReadKey = secondTableEntry.key();
                }
                return removed;
            }
        };
    }

    private NavigableMap<String, BaseEntry<String>> mapWithTwoEntries(BaseEntry<String> e1, BaseEntry<String> e2) {
        NavigableMap<String, BaseEntry<String>> map = new TreeMap<>();
        map.put(e1.key(), e1);
        map.put(e2.key(), e2);
        return map;
    }
}
