package ok.dht.test.shestakova.dao;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import ok.dht.test.shestakova.dao.base.BaseEntry;
import ok.dht.test.shestakova.dao.base.Config;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

final class Storage implements Closeable {

    private static final long VERSION = 0;
    private static final int INDEX_HEADER_SIZE = Long.BYTES * 2;
    private static final int INDEX_RECORD_SIZE = Long.BYTES;

    private static final String FILE_NAME = "data";
    private static final String FILE_EXT = ".dat";
    private static final String FILE_EXT_TMP = ".tmp";

    // supposed to have fresh files first
    private final ResourceScope scope;
    private final List<MemorySegment> sstables;

    private Storage(ResourceScope scope, List<MemorySegment> sstables) {
        this.scope = scope;
        this.sstables = sstables;
    }

    static Storage load(Config config) throws IOException {
        Path basePath = config.basePath();

        List<MemorySegment> sstables = new ArrayList<>();
        ResourceScope scope = ResourceScope.newSharedScope();

        int filesCount;
        try (Stream<Path> files = Files.list(basePath)) {
            filesCount = (int) files.count();
        }

        // FIX-ME check existing files
        for (int i = 0; i < filesCount; i++) {
            Path nextFile = basePath.resolve(FILE_NAME + i + FILE_EXT);
            try {
                sstables.add(mapForRead(scope, nextFile));
            } catch (NoSuchFileException e) {
                break;
            }
        }

        Collections.reverse(sstables);

        return new Storage(scope, sstables);
    }

    static Path save(
            Config config,
            Storage previousState,
            Iterator<BaseEntry<MemorySegment>> entries) throws IOException {
        if (!entries.hasNext()) {
            return null;
        }

        int sstablesCount = previousState.sstables.size();
        Path sstableTmpPath = config.basePath().resolve(FILE_NAME + sstablesCount + FILE_EXT_TMP);

        Files.deleteIfExists(sstableTmpPath);
        Files.createFile(sstableTmpPath);

        ArrayList<BaseEntry<MemorySegment>> entriesList = new ArrayList<>();

        try (ResourceScope writeScope = ResourceScope.newConfinedScope()) {
            long size = 0;
            long entriesCount = 0;

            while (entries.hasNext()) {
                BaseEntry<MemorySegment> entry = entries.next();
                entriesList.add(entry);

                if (entry.value() == null) {
                    size += Long.BYTES + entry.key().byteSize() + Long.BYTES;
                } else {
                    size += Long.BYTES + entry.value().byteSize() + entry.key().byteSize() + Long.BYTES;
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
            for (BaseEntry<MemorySegment> entry : entriesList) {
                MemoryAccess.setLongAtOffset(nextSSTable, INDEX_HEADER_SIZE + index * INDEX_RECORD_SIZE, offset);

                offset += writeRecord(nextSSTable, offset, entry.key());
                offset += writeRecord(nextSSTable, offset, entry.value());

                index++;
            }

            MemoryAccess.setLongAtOffset(nextSSTable, 0, VERSION);
            MemoryAccess.setLongAtOffset(nextSSTable, 8, entriesCount);

            nextSSTable.force();
        }
        return sstableTmpPath;
    }

    public static void deleteFiles(Config config) throws IOException {
        try (Stream<Path> stream = Files.list(config.basePath())) {
            stream
                    .filter(file -> !file.toString().contains(FILE_EXT_TMP))
                    .forEach(f -> {
                        try {
                            Files.delete(f);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }

    public static void moveFile(Config config, Path source, long index) throws IOException {
        Path sstablePath = config.basePath().resolve(FILE_NAME + index + FILE_EXT);
        Files.move(source, sstablePath, StandardCopyOption.ATOMIC_MOVE);
    }

    public static long getFilesCount(Config config) throws IOException {
        long filesCount = 0;

        if (Files.exists(config.basePath())) {
            try (Stream<Path> files = Files.list(config.basePath())) {
                filesCount = files.count();
            }
        }

        return filesCount;
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

    // file structure:
    // (fileVersion)(entryCount)((entryPosition)...)|((keySize/key/valueSize/value)...)
    private long greaterOrEqualEntryIndex(MemorySegment sstable, MemorySegment key) {
        long fileVersion = MemoryAccess.getLongAtOffset(sstable, 0);
        if (fileVersion != 0) {
            throw new IllegalStateException("Unknown file version: " + fileVersion);
        }

        long left = 0;
        long right = MemoryAccess.getLongAtOffset(sstable, 8) - 1;

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

        return left;
    }

    private BaseEntry<MemorySegment> entryAt(MemorySegment sstable, long keyIndex) {
        long offset = MemoryAccess.getLongAtOffset(sstable, INDEX_HEADER_SIZE + keyIndex * INDEX_RECORD_SIZE);
        long keySize = MemoryAccess.getLongAtOffset(sstable, offset);
        long valueOffset = offset + Long.BYTES + keySize;
        long valueSize = MemoryAccess.getLongAtOffset(sstable, valueOffset);

        return new BaseEntry<>(
                sstable.asSlice(offset + Long.BYTES, keySize),
                valueSize == -1 ? null : sstable.asSlice(valueOffset + Long.BYTES, valueSize)
        );
    }

    private Iterator<BaseEntry<MemorySegment>> iterate(MemorySegment sstable, MemorySegment keyFrom,
                                                       MemorySegment keyTo) {
        long keyFromPos = keyFrom == null ? 0 : greaterOrEqualEntryIndex(sstable, keyFrom);
        long keyToPos = keyTo == null ? MemoryAccess.getLongAtOffset(sstable, 8)
                : greaterOrEqualEntryIndex(sstable, keyTo);

        return new Iterator<>() {
            long pos = keyFromPos;

            @Override
            public boolean hasNext() {
                return pos < keyToPos;
            }

            @Override
            public BaseEntry<MemorySegment> next() {
                BaseEntry<MemorySegment> entry = entryAt(sstable, pos);
                pos++;
                return entry;
            }
        };
    }

    public Iterator<BaseEntry<MemorySegment>> iterate(MemorySegment keyFrom, MemorySegment keyTo) {
        List<IndexedPeekIterator<BaseEntry<MemorySegment>>> peekIterators = new ArrayList<>();
        int index = 0;

        for (MemorySegment sstable : sstables) {
            Iterator<BaseEntry<MemorySegment>> iterator = iterate(sstable, keyFrom, keyTo);
            peekIterators.add(new IndexedPeekIterator<>(index, iterator));
            index++;
        }

        return MergeIterator.of(peekIterators, EntryKeyComparator.INSTANCE);
    }

    @Override
    public void close() {
        if (scope.isAlive()) {
            scope.close();
        }
    }

    public boolean isClosed() {
        return !scope.isAlive();
    }
}
