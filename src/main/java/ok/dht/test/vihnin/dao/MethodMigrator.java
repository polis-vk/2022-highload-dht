package ok.dht.test.vihnin.dao;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import ok.dht.test.vihnin.dao.common.Config;
import ok.dht.test.vihnin.dao.common.Entry;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;

public final class MethodMigrator {

    private MethodMigrator() {

    }

    static void save(
            Config config,
            Storage previousState,
            Collection<Entry<MemorySegment>> entries) throws IOException {
        int nextSSTableIndex = previousState.sstables.size();
        Path sstablePath = config.basePath().resolve(Storage.FILE_NAME + nextSSTableIndex + Storage.FILE_EXT);
        save(entries::iterator, sstablePath);
    }

    static void save(
            Storage.Data entries,
            Path sstablePath
    ) throws IOException {

        Path sstableTmpPath = sstablePath.resolveSibling(
                sstablePath.getFileName().toString() + Storage.FILE_EXT_TMP);

        Files.deleteIfExists(sstableTmpPath);
        Files.createFile(sstableTmpPath);

        try (ResourceScope writeScope = ResourceScope.newConfinedScope()) {
            long size = 0;
            long entriesCount = 0;
            boolean hasTombstone = false;
            for (Entry<MemorySegment> entry: entries) {
                size += getSize(entry);
                if (entry.isTombstone()) {
                    hasTombstone = true;
                }
                entriesCount++;
            }

            long dataStart = Storage.INDEX_HEADER_SIZE + Storage.INDEX_RECORD_SIZE * entriesCount;

            MemorySegment nextSSTable = MemorySegment.mapFile(
                    sstableTmpPath,
                    0,
                    dataStart + size,
                    FileChannel.MapMode.READ_WRITE,
                    writeScope
            );

            long index = 0;
            long offset = dataStart;
            for (Entry<MemorySegment> entry: entries) {
                MemoryAccess.setLongAtOffset(nextSSTable,
                        Storage.INDEX_HEADER_SIZE + index * Storage.INDEX_RECORD_SIZE, offset);

                offset += Storage.writeRecord(nextSSTable, offset, entry.key());
                offset += Storage.writeRecord(nextSSTable, offset, entry.value());

                index++;
            }

            MemoryAccess.setLongAtOffset(nextSSTable, 0, Storage.VERSION);
            MemoryAccess.setLongAtOffset(nextSSTable, 8, entriesCount);
            MemoryAccess.setLongAtOffset(nextSSTable, 16, hasTombstone ? 1 : 0);

            nextSSTable.force();
        }

        Files.move(sstableTmpPath, sstablePath, StandardCopyOption.ATOMIC_MOVE);
    }

    private static long getSize(Entry<MemorySegment> entry) {
        if (entry.value() == null) {
            return Long.BYTES + entry.key().byteSize() + Long.BYTES;
        } else {
            return Long.BYTES + entry.value().byteSize() + entry.key().byteSize() + Long.BYTES;
        }
    }

    public static long getSizeOnDisk(Entry<MemorySegment> entry) {
        return getSize(entry) + Storage.INDEX_RECORD_SIZE;
    }
}
