package ok.dht.test.ponomarev.dao.store;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.ponomarev.dao.TimestampEntry;
import ok.dht.test.ponomarev.dao.TombstoneSkipIterator;
import ok.dht.test.ponomarev.dao.Utils;
import ok.dht.test.ponomarev.dao.sstable.SSTable;

public final class Storage implements Closeable {
    private static final String SSTABLE_DIR_PREFIX = "SSTable_";
    private static final String TIMESTAMP_DELIM = "_T_";

    private final Path path;
    private final CopyOnWriteArrayList<SSTable> ssTables;

    private final AtomicBoolean flushIsRunning = new AtomicBoolean(false);

    private volatile AtomicData atomicData;

    public Storage(Path path) throws IOException {
        this.path = path;
        this.ssTables = wakeUpSSTables(path);
        this.atomicData = new AtomicData(
            new ConcurrentSkipListMap<>(Utils.COMPARATOR),
            new ConcurrentSkipListMap<>(Utils.COMPARATOR)
        );
    }

    @Override
    public void close() {
        for (SSTable ssTable : ssTables) {
            ssTable.close();
        }
    }

    public synchronized long flush(long timestamp) throws IOException {
        try {
            if (flushIsRunning.getAndSet(true)) {
                throw new IllegalStateException("FLUSH:  Flushing is going on.");
            }

            atomicData = AtomicData.beforeFlush(atomicData);

            if (atomicData.flushData.isEmpty()) {
                return 0;
            }

            FlushedData flushedData = flushAndCreateSSTable(atomicData.flushData, timestamp);
            ssTables.add(flushedData.ssTable);

            return flushedData.sizeBytes;
        } finally {
            flushIsRunning.set(false);
        }
    }

    public synchronized void compact(long timestamp) throws IOException {
        if (flushIsRunning.get()) {
            throw new IllegalStateException("COMPACT: Flushing is going on.");
        }

        atomicData = AtomicData.beforeFlush(atomicData);
        final Iterator<TimestampEntry> dataIterator = new TombstoneSkipIterator<>(get(null, null));
        if (!dataIterator.hasNext()) {
            return;
        }

        final SortedMap<MemorySegment, TimestampEntry> data = new ConcurrentSkipListMap<>(Utils.COMPARATOR);
        while (dataIterator.hasNext()) {
            TimestampEntry entry = dataIterator.next();
            data.put(entry.key(), entry);
        }

        final SSTable flushedSSTable = flushAndCreateSSTable(data, timestamp).ssTable;
        ssTables.forEach(ssTable -> {
            if (ssTable.getCreatedTime() < timestamp) {
                ssTable.close();
            }
        });
        ssTables.removeIf(ssTable -> ssTable.getCreatedTime() < timestamp);
        ssTables.add(flushedSSTable);

        removeFilesWithNested(getSSTablesOlderThan(path, timestamp));

        atomicData = AtomicData.afterFlush(atomicData);
    }

    private static List<Path> getSSTablesOlderThan(Path path, long timestamp) throws IOException {
        try (Stream<Path> files = Files.walk(path)) {
            return files
                .filter(f -> f.getFileName().toString().contains(SSTABLE_DIR_PREFIX))
                .filter(f -> !f.getFileName().toString().contains(getTimeMark(timestamp)))
                .toList();
        }
    }

    private static void removeFilesWithNested(List<Path> files) throws IOException {
        for (Path dirs : files) {
            try (Stream<Path> ssTableFiles = Files.walk(dirs)) {
                final Iterator<Path> filesToRemove = ssTableFiles.sorted(Comparator.reverseOrder()).iterator();
                while (filesToRemove.hasNext()) {
                    Files.delete(filesToRemove.next());
                }
            }
        }
    }

    private FlushedData flushAndCreateSSTable(
        SortedMap<MemorySegment, TimestampEntry> data,
        long timestamp) throws IOException {
        final long sizeBytes = data.values()
            .stream()
            .mapToLong(TimestampEntry::getSizeBytes)
            .sum();

        final Path sstableDir = path.resolve(SSTABLE_DIR_PREFIX + createHash(timestamp));
        Files.createDirectory(sstableDir);

        SSTable flushedSSTable = SSTable.createInstance(
            sstableDir,
            data.values().iterator(),
            sizeBytes,
            data.size(),
            timestamp
        );

        return new FlushedData(
            flushedSSTable,
            sizeBytes
        );
    }

    private final class FlushedData {
        public final SSTable ssTable;
        public final long sizeBytes;

        public FlushedData(SSTable ssTable, long sizeBytes) {
            this.ssTable = ssTable;
            this.sizeBytes = sizeBytes;
        }
    }

    private static String createHash(long timestamp) {
        final int HASH_SIZE = 40;

        StringBuilder hash = new StringBuilder(getTimeMark(timestamp))
            .append("_H_")
            .append(System.nanoTime());

        while (hash.length() < HASH_SIZE) {
            hash.append(0);
        }

        return hash.substring(0, HASH_SIZE);
    }

    private static String getTimeMark(long timestamp) {
        return TIMESTAMP_DELIM + timestamp + TIMESTAMP_DELIM;
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
        atomicData.memTable.put(entry.key(), entry);
    }

    private static CopyOnWriteArrayList<SSTable> wakeUpSSTables(Path path) throws IOException {
        try (Stream<Path> files = Files.list(path)) {
            final List<String> tableDirNames = files
                .map(f -> f.getFileName().toString())
                .filter(n -> n.contains(SSTABLE_DIR_PREFIX))
                .sorted()
                .toList();

            final CopyOnWriteArrayList<SSTable> tables = new CopyOnWriteArrayList<>();
            for (String name : tableDirNames) {
                final String[] split = name.split(TIMESTAMP_DELIM);
                if (split.length != 3) {
                    throw new IllegalStateException("Invalid SSTable dir name");
                }

                final long createdTime = Long.parseLong(split[1]);
                tables.add(SSTable.upInstance(path.resolve(name), createdTime));
            }

            return tables;
        }
    }
}
