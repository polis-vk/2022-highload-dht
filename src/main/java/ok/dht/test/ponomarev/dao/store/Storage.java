package ok.dht.test.ponomarev.dao.store;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.ponomarev.dao.TimestampEntry;
import ok.dht.test.ponomarev.dao.TombstoneSkipIterator;
import ok.dht.test.ponomarev.dao.Utils;
import ok.dht.test.ponomarev.dao.sstable.SSTable;

public final class Storage implements Closeable {
    private final Path path;
    private final CopyOnWriteArrayList<SSTable> ssTables;

    private volatile AtomicData atomicData;

    public Storage(Path path) throws IOException {
        this.path = path;
        this.ssTables = new CopyOnWriteArrayList<>(SSTable.wakeUpSSTables(path));
        this.atomicData = new AtomicData(
                new ConcurrentSkipListMap<>(Utils.COMPARATOR),
                new AtomicLong(),
                new ConcurrentSkipListMap<>(Utils.COMPARATOR),
                new AtomicLong()
        );
    }

    @Override
    public void close() {
        for (SSTable ssTable : ssTables) {
            ssTable.close();
        }
    }

    public void beforeFlush() {
        atomicData = AtomicData.beforeFlush(atomicData);
    }

    public void afterFlush() {
        atomicData = AtomicData.afterFlush(atomicData);
    }

    public long getMemTableDataSize() {
        return this.atomicData.memTableSizeBytes.get();
    }

    public synchronized long flush(long timestamp) throws IOException {
        //TODO: Может не стоит допускать таких флашей?
        if (atomicData.flushData.isEmpty()) {
            return 0;
        }

        ssTables.add(
                SSTable.createInstance(
                        path,
                        new SSTable.DataWithInfo(
                                atomicData.flushData.values().iterator(),
                                atomicData.flushDataSizeBytes.get(),
                                atomicData.flushData.size()
                        )
                )
        );

        return atomicData.flushDataSizeBytes.get();
    }

    public synchronized void compact(long timestamp) throws IOException {
        final Iterator<TimestampEntry> dataIterator = new TombstoneSkipIterator<>(get(null, null));
        //TODO: Нужно очищать SSTable т.к. данных нет
        if (!dataIterator.hasNext()) {
            return;
        }

        final SortedMap<MemorySegment, TimestampEntry> data = new ConcurrentSkipListMap<>(Utils.COMPARATOR);

        long dataSize = 0;
        while (dataIterator.hasNext()) {
            TimestampEntry entry = dataIterator.next();
            dataSize += entry.getSizeBytes();
            data.put(entry.key(), entry);
        }

        final SSTable flushedSSTable = SSTable.createInstance(
                path,
                new SSTable.DataWithInfo(
                        data.values().iterator(),
                        dataSize,
                        data.size()
                )
        );

        for (var sstable : ssTables) {
            if (sstable.getCreatedTime() >= timestamp) {
                continue;
            }
            sstable.remove();

            ssTables.remove(sstable);
        }
        ssTables.add(flushedSSTable);
    }

    public TimestampEntry get(MemorySegment key) {
        final TimestampEntry memoryEntry = atomicData.memTable.get(key);
        if (memoryEntry != null) {
            return memoryEntry.value() == null ? null : memoryEntry;
        }

        final Iterator<TimestampEntry> data = get(key, null);
        if (!data.hasNext()) {
            return null;
        }

        final TimestampEntry entry = data.next();
        if (Utils.compare(key, entry.key()) == 0) {
            return entry.value() == null ? null : entry;
        }

        return null;
    }

    public Iterator<TimestampEntry> get(MemorySegment from, MemorySegment to) {
        final List<Iterator<TimestampEntry>> entries = new ArrayList<>(ssTables.size() + 2);
        for (SSTable ssTable : ssTables) {
            entries.add(ssTable.get(from, to));
        }

        entries.add(slice(atomicData.flushData, from, to));
        entries.add(slice(atomicData.memTable, from, to));

        return MergeIterator.of(entries, Utils.COMPARATOR);
    }

    private static Iterator<TimestampEntry> slice(
            SortedMap<MemorySegment, TimestampEntry> store,
            MemorySegment from,
            MemorySegment to
    ) {
        if (store == null || store.isEmpty()) {
            return Collections.emptyIterator();
        }

        if (from == null && to == null) {
            return store.values().iterator();
        }

        if (from == null) {
            return store.headMap(to).values().iterator();
        }

        if (to == null) {
            return store.tailMap(from).values().iterator();
        }

        return store.subMap(from, to).values().iterator();
    }

    public void upsert(TimestampEntry entry) {
        final TimestampEntry updated = atomicData.memTable.computeIfPresent(entry.key(), (k, v) -> {
            atomicData.memTableSizeBytes.addAndGet(entry.getSizeBytes() - v.getSizeBytes());

            return entry;
        });

        if (updated != null) {
            return;
        }

        atomicData.memTable.computeIfAbsent(entry.key(), (k) -> {
            atomicData.memTableSizeBytes.addAndGet(entry.getSizeBytes());

            return entry;
        });
    }
}

