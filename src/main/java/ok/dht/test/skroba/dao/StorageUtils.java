package ok.dht.test.skroba.dao;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import ok.dht.test.skroba.dao.base.Config;
import ok.dht.test.skroba.dao.base.Entry;

import java.io.IOException;
import java.lang.ref.Cleaner;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;

public final class StorageUtils {
    static final int INDEX_HEADER_SIZE = Long.BYTES * 3;
    static final int INDEX_RECORD_SIZE = Long.BYTES;
    private static final Cleaner CLEANER = Cleaner.create((Runnable r) ->
            new Thread(r, "Storage-Cleaner") {
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
    private static final String FILE_NAME = "data";
    private static final String FILE_EXT = ".dat";
    private static final String FILE_EXT_TMP = ".tmp";
    private static final String COMPACTED_FILE = FILE_NAME + "_compacted_" + FILE_EXT;
    
    private StorageUtils() {
        //Nothing
    }
    
    static Storage load(Config config) throws IOException {
        Path basePath = config.basePath();
        Path compactedFile = config.basePath().resolve(COMPACTED_FILE);
        if (Files.exists(compactedFile)) {
            finishCompact(config, compactedFile);
        }
        
        ArrayList<MemorySegment> sstables = new ArrayList<>();
        ResourceScope scope = ResourceScope.newSharedScope(CLEANER);
        
        for (int i = 0; true; i++) {
            Path nextFile = basePath.resolve(FILE_NAME + i + FILE_EXT);
            try {
                sstables.add(mapForRead(scope, nextFile));
            } catch (NoSuchFileException e) {
                break;
            }
        }
        
        boolean hasTombstones = !sstables.isEmpty()
                && MemoryAccess.getLongAtOffset(sstables.get(0), 16) == 1;
        return new Storage(scope, sstables, hasTombstones);
    }
    
    // it is supposed that entries can not be changed externally during this method call
    static void save(
            Config config,
            Storage previousState,
            Collection<Entry<MemorySegment>> entries) throws IOException {
        int nextSSTableIndex = previousState.sstables.size();
        Path sstablePath = config.basePath().resolve(
                FILE_NAME + nextSSTableIndex + FILE_EXT
        );
        save(entries::iterator, sstablePath);
    }
    
    private static void save(
            Iterable<Entry<MemorySegment>> entries,
            Path sstablePath
    ) throws IOException {
        
        Path sstableTmpPath = sstablePath.resolveSibling(
                sstablePath.getFileName().toString() + FILE_EXT_TMP
        );
        
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
                    java.nio.channels.FileChannel.MapMode.READ_WRITE,
                    writeScope
            );
            
            long index = 0;
            long offset = dataStart;
            for (Entry<MemorySegment> entry : entries) {
                MemoryAccess.setLongAtOffset(
                        nextSSTable,
                        INDEX_HEADER_SIZE + index * INDEX_RECORD_SIZE,
                        offset
                );
                
                offset += writeRecord(nextSSTable, offset, entry.key());
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
            return Long.BYTES + entry.key().byteSize() + Long.BYTES;
        } else {
            return Long.BYTES + entry.value().byteSize() + entry.key().byteSize() + Long.BYTES;
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
    
    @SuppressWarnings("DuplicateThrows")
    private static MemorySegment mapForRead(ResourceScope scope, Path file) throws NoSuchFileException, IOException {
        long size = Files.size(file);
        
        return MemorySegment.mapFile(file, 0, size, FileChannel.MapMode.READ_ONLY, scope);
    }
    
    public static void compact(Config config, Iterable<Entry<MemorySegment>> data) throws IOException {
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
        
        Files.move(
                compactedFile,
                config.basePath().resolve(FILE_NAME + 0 + FILE_EXT),
                StandardCopyOption.ATOMIC_MOVE
        );
    }
    
}
