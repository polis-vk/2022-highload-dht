package ok.dht.test.kondraev.dao;

import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;

import java.io.IOException;
import java.lang.ref.Cleaner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.stream.Stream;

import static ok.dht.test.kondraev.dao.Dao.allStored;
import static ok.dht.test.kondraev.dao.Files.filenameOf;

public class Storage {
    private static final String COMPACT_NAME = "compacted";
    private static final String TABLE_PREFIX = "table";
    private static final String TMP_SUFFIX = "-temp";

    private static final Cleaner CLEANER = Cleaner.create((Runnable r) -> new Thread(r, "Storage-Cleaner") {
        @Override
        public synchronized void start() {
            setDaemon(true);
            super.start();
        }
    });

    /**
     * ordered from most recent to the earliest.
    */
    private final List<SortedStringTable> sortedStringTables;
    private final Path basePath;
    private final Path compactDir;
    private final Path compactDirTmp;
    private final ResourceScope scope;
    private final boolean hasTombstones;

    public Storage(
            List<SortedStringTable> sortedStringTables,
            Path basePath,
            ResourceScope scope,
            boolean hasTombstones
    ) {
        this.sortedStringTables = sortedStringTables;
        this.basePath = basePath;
        compactDir = basePath.resolve(COMPACT_NAME);
        compactDirTmp = basePath.resolve(COMPACT_NAME + TMP_SUFFIX);
        this.scope = scope;
        this.hasTombstones = hasTombstones;
    }

    public MemorySegmentEntry get(MemorySegment key) throws IOException {
        for (SortedStringTable table : sortedStringTables) {
            MemorySegmentEntry result = table.get(key);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    public static Storage load(Path basePath) throws IOException {
        List<SortedStringTable> sortedStringTables = new ArrayList<>();
        ResourceScope scope = ResourceScope.newSharedScope(CLEANER);
        try (Stream<Path> stream = Files.list(basePath)) {
            Iterator<Path> pathIterator = stream
                    .filter(subDirectory -> filenameOf(subDirectory).startsWith(TABLE_PREFIX))
                    .sorted(Comparator.comparing(ok.dht.test.kondraev.dao.Files::filenameOf).reversed())
                    .iterator();
            while (pathIterator.hasNext()) {
                // TODO perf
                sortedStringTables.add(SortedStringTable.of(pathIterator.next(), scope));
            }
        }
        boolean hasTombstones = !sortedStringTables.isEmpty() && sortedStringTables.get(0).hasTombstones;
        Storage storage = new Storage(sortedStringTables, basePath, scope, hasTombstones);
        if (Files.exists(storage.compactDirTmp)) {
            SortedStringTable.destroyFiles(storage.compactDirTmp);
            storage.compact(allStored(sortedStringTables.spliterator()));
            return storage;
        }
        if (Files.exists(storage.compactDir)) {
            storage.finishCompact();
        }
        return storage;
    }

    private static String sortedStringTablePath(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("Negative index");
        }
        // 10^10 > Integer.MAX_VALUE
        String value = String.valueOf(index);
        char[] zeros = new char[10 - value.length()];
        Arrays.fill(zeros, '0');
        return TABLE_PREFIX + new String(zeros) + value;
    }

    public Spliterator<SortedStringTable> spliterator() {
        return sortedStringTables.spliterator();
    }

    public boolean isCompacted() {
        return !hasTombstones;
    }

    public Storage store(Collection<MemorySegmentEntry> values) throws IOException {
        Path tablePath = Files.createDirectory(
                basePath.resolve(sortedStringTablePath(sortedStringTables.size()))
        );
        List<SortedStringTable> tables = new ArrayList<>(1 + sortedStringTables.size());
        SortedStringTable table = SortedStringTable.save(tablePath, values, scope);
        tables.add(table);
        tables.addAll(sortedStringTables);
        return new Storage(tables, basePath, scope, table.hasTombstones);
    }

    public void compact(Iterator<MemorySegmentEntry> data) throws IOException {
        try (ResourceScope confinedScope = ResourceScope.newConfinedScope()) {
            SortedStringTable.save(Files.createDirectory(compactDirTmp), data, confinedScope);
        }
        Files.move(compactDirTmp, compactDir, StandardCopyOption.ATOMIC_MOVE);
    }

    public Storage finishCompact() throws IOException {
        for (int i = sortedStringTables.size() - 1; i >= 0; i--) {
            SortedStringTable.destroyFiles(basePath.resolve(Storage.sortedStringTablePath(i)));
        }

        Path table0 = basePath.resolve(Storage.sortedStringTablePath(0));
        Files.move(compactDir, table0, StandardCopyOption.ATOMIC_MOVE);
        SortedStringTable table = SortedStringTable.of(table0, scope);
        if (table.hasTombstones) {
            throw new IllegalStateException("Compacted table has tombstones");
        }
        return new Storage(List.of(table), basePath, scope, false);
    }

    public void close() {
        while (scope.isAlive()) {
            try {
                scope.close();
                return;
            } catch (IllegalStateException ignored) {
                // continue trying on exception
            }
        }
    }

    public boolean isClosed() {
        return !scope.isAlive();
    }
}
