package ok.dht.test.shakhov.dao;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;

import java.io.IOException;
import java.lang.ref.Cleaner;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;

final class StorageUtils {
    static final Cleaner CLEANER = Cleaner.create(r ->
            new Thread("Storage-Cleaner") {
                @Override
                public synchronized void start() {
                    setDaemon(true);
                    super.start();
                }

                @Override
                public void run() {
                    r.run();
                }
            });

    private static final long VERSION = 0;
    static final int INDEX_HEADER_SIZE = Long.BYTES * 3;
    static final int INDEX_RECORD_SIZE = Long.BYTES;
    static final String FILE_NAME = "data";
    static final String FILE_EXT = ".dat";
    static final String COMPACTED_FILE = FILE_NAME + "_compacted_" + FILE_EXT;
    private static final String FILE_EXT_TMP = ".tmp";

    private StorageUtils() {
    }

    static void save(
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
                size += getSize(entry);
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
                offset += writeTimestamp(nextSSTable, offset, entry.timestamp());
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

    private static long getSize(Entry<MemorySegment> entry) {
        if (entry.value() == null) {
            return Long.BYTES + entry.key().byteSize() + Long.BYTES + Long.BYTES;
        } else {
            return Long.BYTES + entry.value().byteSize() + Long.BYTES + entry.key().byteSize() + Long.BYTES;
        }
    }

    public static long getSizeOnDisk(Entry<MemorySegment> entry) {
        return getSize(entry) + INDEX_RECORD_SIZE;
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

    private static long writeTimestamp(MemorySegment nextSSTable, long offset, long timestamp) {
        MemoryAccess.setLongAtOffset(nextSSTable, offset, timestamp);
        return Long.BYTES;
    }

    @SuppressWarnings("DuplicateThrows")
    static MemorySegment mapForRead(ResourceScope scope, Path file) throws NoSuchFileException, IOException {
        long size = Files.size(file);

        return MemorySegment.mapFile(file, 0, size, FileChannel.MapMode.READ_ONLY, scope);
    }

    public static void compact(DaoConfig daoConfig, Data data) throws IOException {
        Path compactedFile = daoConfig.basePath().resolve(COMPACTED_FILE);
        save(data, compactedFile);
        finishCompact(daoConfig, compactedFile);
    }

    static void finishCompact(DaoConfig daoConfig, Path compactedFile) throws IOException {
        for (int i = 0; ; i++) {
            Path nextFile = daoConfig.basePath().resolve(FILE_NAME + i + FILE_EXT);
            if (!Files.deleteIfExists(nextFile)) {
                break;
            }
        }

        Path basePath = daoConfig.basePath();
        Files.move(compactedFile, basePath.resolve(FILE_NAME + 0 + FILE_EXT), StandardCopyOption.ATOMIC_MOVE);
    }

    public interface Data {
        Iterator<Entry<MemorySegment>> iterator() throws IOException;
    }
}
