package ok.dht.test.komissarov.database;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import ok.dht.test.komissarov.database.iterators.IntervalIterator;
import ok.dht.test.komissarov.database.models.Config;
import ok.dht.test.komissarov.database.models.Entry;
import ok.dht.test.komissarov.database.utils.MemorySegmentComparator;
import ok.dht.test.komissarov.database.utils.StorageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class Storage implements Closeable {

    private static final long VERSION = 0;
    private static final int INDEX_HEADER_SIZE = Long.BYTES * 3;
    private static final int INDEX_RECORD_SIZE = Long.BYTES;

    private static final Logger LOG = LoggerFactory.getLogger(Storage.class);
    private static final String FILE_NAME = "data";
    private static final String FILE_EXT = ".dat";
    private static final String FILE_EXT_TMP = ".tmp";
    private static final String COMPACTED_FILE = FILE_NAME + "_compacted_" + FILE_EXT;

    private final ResourceScope scope;
    private final List<MemorySegment> sstables;
    private final boolean hasTombstones;

    static Storage load(Config config) throws IOException {
        Path basePath = config.basePath();
        Path compactedFile = config.basePath().resolve(COMPACTED_FILE);
        if (Files.exists(compactedFile)) {
            finishCompact(config, compactedFile);
        }

        ArrayList<MemorySegment> sstables = new ArrayList<>();
        ResourceScope scope = ResourceScope.newSharedScope(StorageUtils.getCleaner());

        for (int i = 0; i < Integer.MAX_VALUE; i++) {
            Path nextFile = basePath.resolve(FILE_NAME + i + FILE_EXT);
            try {
                sstables.add(mapForRead(scope, nextFile));
            } catch (NoSuchFileException e) {
                break;
            }
        }

        boolean hasTombstones = !sstables.isEmpty() && MemoryAccess.getLongAtOffset(sstables.get(0), 16) == 1;
        return new Storage(scope, sstables, hasTombstones);
    }

    // it is supposed that entries can not be changed externally during this method call
    static void save(
            Config config,
            Storage previousState,
            Collection<Entry<MemorySegment>> entries) throws IOException {
        int nextSSTableIndex = previousState.sstables.size();
        Path sstablePath = config.basePath().resolve(FILE_NAME + nextSSTableIndex + FILE_EXT);
        save(entries::iterator, sstablePath);
    }

    private static void save(
            Data entries,
            Path sstablePath
    ) throws IOException {

        Path sstableTmpPath = sstablePath.resolveSibling(sstablePath.getFileName().toString() + FILE_EXT_TMP);

        Files.deleteIfExists(sstableTmpPath);
        Files.createFile(sstableTmpPath);

        try (ResourceScope writeScope = ResourceScope.newConfinedScope()) {
            long size = 0;
            long entriesCount = 0;
            boolean hasTombstone = false;

            Iterator<Entry<MemorySegment>> iterator = entries.iterator();
            while (iterator.hasNext()) {
                Entry<MemorySegment> entry = iterator.next();
                size += StorageUtils.getSize(entry);
                if (entry.isTombstone()) {
                    hasTombstone = true;
                }
                entriesCount++;
            }

            long dataStart = INDEX_HEADER_SIZE + INDEX_RECORD_SIZE * entriesCount;

            MemorySegment nextSSTable = MemorySegment.mapFile(
                            sstableTmpPath,
                            0,
                            dataStart + size,
                            FileChannel.MapMode.READ_WRITE,
                            writeScope
            );

            long index = 0;
            long offset = dataStart;

            iterator = entries.iterator();
            while (iterator.hasNext()) {
                Entry<MemorySegment> entry = iterator.next();
                MemoryAccess.setLongAtOffset(nextSSTable, INDEX_HEADER_SIZE + index * INDEX_RECORD_SIZE, offset);

                offset += writeRecord(nextSSTable, offset, entry.key());
                MemoryAccess.setLongAtOffset(nextSSTable, offset, entry.timestamp());
                offset += Long.BYTES;
                offset += writeRecord(nextSSTable, offset, entry.value());

                index++;
            }

            MemoryAccess.setLongAtOffset(nextSSTable, 0, VERSION);
            MemoryAccess.setLongAtOffset(nextSSTable, 8, entriesCount);
            MemoryAccess.setLongAtOffset(nextSSTable, 16, hasTombstone ? 1 : 0);

            nextSSTable.force();
        }

        Files.move(sstableTmpPath, sstablePath, StandardCopyOption.ATOMIC_MOVE);
    }

    public static long getSizeOnDisk(Entry<MemorySegment> entry) {
        return StorageUtils.getSize(entry) + INDEX_RECORD_SIZE;
    }

    private static long writeRecord(MemorySegment nextSSTable, long offset, MemorySegment record) {
        if (record == null) {
            MemoryAccess.setLongAtOffset(nextSSTable, offset, -1);
            return Long.BYTES;
        }
        long recordSize = record.byteSize();
        MemoryAccess.setLongAtOffset(nextSSTable, offset, recordSize);
        nextSSTable.asSlice(offset + Long.BYTES, recordSize).copyFrom(record);
        return Long.BYTES + recordSize;
    }

    @SuppressWarnings("DuplicateThrows")
    private static MemorySegment mapForRead(ResourceScope scope, Path file) throws NoSuchFileException, IOException {
        long size = Files.size(file);

        return MemorySegment.mapFile(file, 0, size, FileChannel.MapMode.READ_ONLY, scope);
    }

    public static void compact(Config config, Data data) throws IOException {
        Path compactedFile = config.basePath().resolve(COMPACTED_FILE);
        save(data, compactedFile);
        finishCompact(config, compactedFile);
    }

    private static void finishCompact(Config config, Path compactedFile) throws IOException {
        for (int i = 0; ; i++) {
            Path nextFile = config.basePath().resolve(FILE_NAME + i + FILE_EXT);
            if (!Files.deleteIfExists(nextFile)) {
                break;
            }
        }

        Files.move(compactedFile, config.basePath().resolve(FILE_NAME + 0 + FILE_EXT), StandardCopyOption.ATOMIC_MOVE);
    }

    private Storage(ResourceScope scope, List<MemorySegment> sstables, boolean hasTombstones) {
        this.scope = scope;
        this.sstables = sstables;
        this.hasTombstones = hasTombstones;
    }

    private long greaterOrEqualEntryIndex(MemorySegment sstable, MemorySegment key) {
        long index = entryIndex(sstable, key);
        if (index < 0) {
            return ~index;
        }
        return index;
    }

    // file structure:
    // (fileVersion)(entryCount)((entryPosition)...)|((keySize/key/valueSize/value)...)
    private long entryIndex(MemorySegment sstable, MemorySegment key) {
        long fileVersion = MemoryAccess.getLongAtOffset(sstable, 0);
        if (fileVersion != 0) {
            throw new IllegalStateException("Unknown file version: " + fileVersion);
        }
        long recordsCount = MemoryAccess.getLongAtOffset(sstable, 8);
        if (key == null) {
            return recordsCount;
        }

        long left = 0;
        long right = recordsCount - 1;

        while (left <= right) {
            long mid = (left + right) >>> 1;

            long keyPos = MemoryAccess.getLongAtOffset(sstable, INDEX_HEADER_SIZE + mid * INDEX_RECORD_SIZE);
            long keySize = MemoryAccess.getLongAtOffset(sstable, keyPos);

            MemorySegment keyForCheck = sstable.asSlice(keyPos + Long.BYTES, keySize);
            int comparedResult = MemorySegmentComparator.INSTANCE.compare(key, keyForCheck);
            if (comparedResult > 0) {
                left = mid + 1;
            } else if (comparedResult < 0) {
                right = mid - 1;
            } else {
                return mid;
            }
        }

        return ~left;
    }

    public Entry<MemorySegment> get(MemorySegment key) {
        try {
            for (int i = sstables.size() - 1; i >= 0; i--) {
                MemorySegment sstable = sstables.get(i);
                long keyFromPos = entryIndex(sstable, key);
                if (keyFromPos >= 0) {
                    return StorageUtils.entryAt(sstable, keyFromPos, scope);
                }
            }
            return null;
        } catch (IllegalStateException e) {
            throw StorageUtils.checkForClose(e, scope);
        }
    }

    private Iterator<Entry<MemorySegment>> iterate(MemorySegment sstable, MemorySegment keyFrom, MemorySegment keyTo) {
        long keyFromPos = greaterOrEqualEntryIndex(sstable, keyFrom);
        long keyToPos = greaterOrEqualEntryIndex(sstable, keyTo);

        return new IntervalIterator(sstable, keyFromPos, keyToPos, scope);
    }

    public List<Iterator<Entry<MemorySegment>>> iterate(MemorySegment keyFrom, MemorySegment keyTo) {
        try {
            ArrayList<Iterator<Entry<MemorySegment>>> iterators = new ArrayList<>(sstables.size());
            for (MemorySegment sstable : sstables) {
                iterators.add(iterate(sstable, keyFrom, keyTo));
            }
            return iterators;
        } catch (IllegalStateException e) {
            throw StorageUtils.checkForClose(e, scope);
        }
    }

    public boolean isClosed() {
        return !scope.isAlive();
    }

    public boolean isCompacted() {
        if (sstables.isEmpty()) {
            return true;
        }
        if (sstables.size() > 1) {
            return false;
        }
        return !hasTombstones;
    }

    @Override
    public void close() throws IOException {
        while (scope.isAlive()) {
            try {
                scope.close();
                return;
            } catch (IllegalStateException e) {
                LOG.info("Cant close");
            }
        }
    }

    public interface Data {
        Iterator<Entry<MemorySegment>> iterator() throws IOException;
    }

}
