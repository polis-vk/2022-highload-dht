package ok.dht.test.anikina.dao;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class StorageUtils {
    static final long VERSION = 0;
    static final int INDEX_HEADER_SIZE = Long.BYTES * 3;
    static final int INDEX_RECORD_SIZE = Long.BYTES;
    static final String FILE_NAME = "data";
    static final String FILE_EXT = ".dat";
    static final String FILE_EXT_TMP = ".tmp";

    private StorageUtils() {
    }

    static void save(Storage.Data entries, Path sstablePath) throws IOException {
        Path sstableTmpPath = sstablePath.resolveSibling(sstablePath.getFileName().toString() + FILE_EXT_TMP);
        Files.deleteIfExists(sstableTmpPath);
        Files.createFile(sstableTmpPath);

        try (ResourceScope writeScope = ResourceScope.newConfinedScope()) {
            long size = 0;
            long entriesCount = 0;
            boolean hasTombstone = false;
            for (Entry<MemorySegment> entry : entries) {
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
            for (Entry<MemorySegment> entry : entries) {
                MemoryAccess.setLongAtOffset(nextSSTable, INDEX_HEADER_SIZE + index * INDEX_RECORD_SIZE, offset);
                offset += writeRecord(nextSSTable, offset, entry.key());
                nextSSTable.asSlice(offset, Long.BYTES).copyFrom(entry.timestamp());
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

    static long getSize(Entry<MemorySegment> entry) {
        long size = Long.BYTES + entry.key().byteSize() + Long.BYTES + Long.BYTES;
        return entry.value() == null ? size : size + entry.value().byteSize();
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

    static void finishCompact(Config config, Path compactedFile) throws IOException {
        for (int i = 0; ; i++) {
            Path nextFile = config.basePath().resolve(FILE_NAME + i + FILE_EXT);
            if (!Files.deleteIfExists(nextFile)) {
                break;
            }
        }
        Files.move(compactedFile, config.basePath().resolve(FILE_NAME + 0 + FILE_EXT), StandardCopyOption.ATOMIC_MOVE);
    }

    static MemorySegment mapForRead(ResourceScope scope, Path file) throws IOException {
        return MemorySegment.mapFile(file, 0, Files.size(file), FileChannel.MapMode.READ_ONLY, scope);
    }
}
