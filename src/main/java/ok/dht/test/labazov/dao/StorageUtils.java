package ok.dht.test.labazov.dao;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;

public final class StorageUtils {
    private static final String FILE_NAME = "data";
    private static final String FILE_EXT = ".dat";
    private static final String FILE_EXT_TMP = ".tmp";
    private static final long VERSION = 0;

    private StorageUtils() {
    }

    static Storage load(Config config) throws IOException {
        Path basePath = config.basePath();
        Path compactedFile = config.basePath().resolve(Storage.COMPACTED_FILE);
        if (Files.exists(compactedFile)) {
            Storage.finishCompact(config, compactedFile);
        }

        ArrayList<MemorySegment> sstables = new ArrayList<>();
        ResourceScope scope = ResourceScope.newSharedScope(Storage.CLEANER);

        int i = 0;
        while (0 < 1) {
            Path nextFile = basePath.resolve(FILE_NAME + i + FILE_EXT);
            try {
                long size = Files.size(nextFile);

                sstables.add(MemorySegment.mapFile(nextFile, 0, size, FileChannel.MapMode.READ_ONLY, scope));
            } catch (NoSuchFileException e) {
                break;
            }
            i++;
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

    static void save(
            Storage.Data entries,
            Path sstablePath
    ) throws IOException {

        Path sstableTmpPath = sstablePath.resolveSibling(sstablePath.getFileName().toString() + FILE_EXT_TMP);

        Files.deleteIfExists(sstableTmpPath);
        Files.createFile(sstableTmpPath);

        try (ResourceScope writeScope = ResourceScope.newConfinedScope()) {
            long size = 0;
            long entriesCount = 0;
            boolean hasTombstone = false;
            for (Entry<MemorySegment> entry : entries) {
                long result;
                if (entry.value() == null) {
                    result = Long.BYTES + entry.key().byteSize() + Long.BYTES;
                } else {
                    result = Long.BYTES + entry.value().byteSize() + entry.key().byteSize() + Long.BYTES;
                }
                size += result;
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
            for (Entry<MemorySegment> entry : entries) {
                MemoryAccess.setLongAtOffset(nextSSTable,
                        Storage.INDEX_HEADER_SIZE + index * Storage.INDEX_RECORD_SIZE, offset);

                offset += Storage.writeRecord(nextSSTable, offset, entry.key());
                offset += Storage.writeRecord(nextSSTable, offset, entry.value());

                index++;
            }

            MemoryAccess.setLongAtOffset(nextSSTable, 0, VERSION);
            MemoryAccess.setLongAtOffset(nextSSTable, 8, entriesCount);
            MemoryAccess.setLongAtOffset(nextSSTable, 16, hasTombstone ? 1 : 0);

            nextSSTable.force();
        }

        Files.move(sstableTmpPath, sstablePath, StandardCopyOption.ATOMIC_MOVE);
    }
}
