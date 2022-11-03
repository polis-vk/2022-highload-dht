package ok.dht.test.anikina.dao;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;

import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.Cleaner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadFactory;

import static ok.dht.test.anikina.dao.StorageUtils.FILE_EXT;
import static ok.dht.test.anikina.dao.StorageUtils.FILE_NAME;
import static ok.dht.test.anikina.dao.StorageUtils.INDEX_HEADER_SIZE;
import static ok.dht.test.anikina.dao.StorageUtils.INDEX_RECORD_SIZE;

class Storage implements Closeable {
    private static final String COMPACTED_FILE = FILE_NAME + "_compacted_" + FILE_EXT;
    private static final Cleaner CLEANER = Cleaner.create(new CleanerThreadFactory());

    private final ResourceScope scope;
    private final List<MemorySegment> ssTables;
    private final boolean hasTombstones;

    private Storage(ResourceScope scope, List<MemorySegment> ssTables, boolean hasTombstones) {
        this.scope = scope;
        this.ssTables = ssTables;
        this.hasTombstones = hasTombstones;
    }

    static Storage load(Config config) throws IOException {
        Path basePath = config.basePath();
        Path compactedFile = config.basePath().resolve(COMPACTED_FILE);
        if (Files.exists(compactedFile)) {
            StorageUtils.finishCompact(config, compactedFile);
        }

        ArrayList<MemorySegment> sstables = new ArrayList<>();
        ResourceScope scope = ResourceScope.newSharedScope(CLEANER);

        Path nextFile = basePath.resolve(FILE_NAME + 0 + FILE_EXT);
        for (int i = 1; Files.exists(nextFile); i++) {
            sstables.add(StorageUtils.mapForRead(scope, nextFile));
            nextFile = basePath.resolve(FILE_NAME + i + FILE_EXT);
        }

        boolean hasTombstones = !sstables.isEmpty() && MemoryAccess.getLongAtOffset(sstables.get(0), 16) == 1;
        return new Storage(scope, sstables, hasTombstones);
    }

    static void save(
            Config config,
            Storage previousState,
            Collection<Entry<MemorySegment>> entries) throws IOException {
        int nextSSTableIndex = previousState.ssTables.size();
        Path sstablePath = config.basePath().resolve(FILE_NAME + nextSSTableIndex + FILE_EXT);
        StorageUtils.save(entries::iterator, sstablePath);
    }

    public static long getSizeOnDisk(Entry<MemorySegment> entry) {
        return StorageUtils.getSize(entry) + INDEX_RECORD_SIZE;
    }

    public static void compact(Config config, Data data) throws IOException {
        Path compactedFile = config.basePath().resolve(COMPACTED_FILE);
        StorageUtils.save(data, compactedFile);
        StorageUtils.finishCompact(config, compactedFile);
    }

    private long greaterOrEqualEntryIndex(MemorySegment sstable, MemorySegment key) {
        long index = entryIndex(sstable, key);
        return index < 0 ? ~index : index;
    }

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

    private Entry<MemorySegment> entryAt(MemorySegment sstable, long keyIndex) {
        try {
            long offset = MemoryAccess.getLongAtOffset(sstable, INDEX_HEADER_SIZE + keyIndex * INDEX_RECORD_SIZE);
            long keySize = MemoryAccess.getLongAtOffset(sstable, offset);
            long timestampOffset = offset + Long.BYTES + keySize;
            long valueOffset = timestampOffset + Long.BYTES;
            long valueSize = MemoryAccess.getLongAtOffset(sstable, valueOffset);
            return new BaseEntry<>(
                    sstable.asSlice(offset + Long.BYTES, keySize),
                    valueSize == -1 ? null : sstable.asSlice(valueOffset + Long.BYTES, valueSize),
                    sstable.asSlice(timestampOffset, Long.BYTES)
            );
        } catch (IllegalStateException e) {
            throw checkForClose(e);
        }
    }

    public Entry<MemorySegment> get(MemorySegment key) {
        try {
            for (int i = ssTables.size() - 1; i >= 0; i--) {
                MemorySegment sstable = ssTables.get(i);
                long keyFromPos = entryIndex(sstable, key);
                if (keyFromPos >= 0) {
                    return entryAt(sstable, keyFromPos);
                }
            }
            return null;
        } catch (IllegalStateException e) {
            throw checkForClose(e);
        }
    }

    private Iterator<Entry<MemorySegment>> iterate(MemorySegment sstable, MemorySegment keyFrom, MemorySegment keyTo) {
        long keyFromPos = greaterOrEqualEntryIndex(sstable, keyFrom);
        long keyToPos = greaterOrEqualEntryIndex(sstable, keyTo);

        return new Iterator<>() {
            long pos = keyFromPos;

            @Override
            public boolean hasNext() {
                return pos < keyToPos;
            }

            @Override
            public Entry<MemorySegment> next() {
                Entry<MemorySegment> entry = entryAt(sstable, pos);
                pos++;
                return entry;
            }
        };
    }

    public List<Iterator<Entry<MemorySegment>>> iterate(MemorySegment keyFrom, MemorySegment keyTo) {
        try {
            ArrayList<Iterator<Entry<MemorySegment>>> iterators = new ArrayList<>(ssTables.size());
            for (MemorySegment sstable : ssTables) {
                iterators.add(iterate(sstable, keyFrom, keyTo));
            }
            return iterators;
        } catch (IllegalStateException e) {
            throw checkForClose(e);
        }
    }

    private RuntimeException checkForClose(IllegalStateException e) {
        if (isClosed()) {
            throw new StorageClosedException(e);
        } else {
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        while (scope.isAlive()) {
            try {
                scope.close();
                return;
            } catch (IllegalStateException ignored) {
                // ignored
            }
        }
    }

    public boolean isClosed() {
        return !scope.isAlive();
    }

    public boolean isCompacted() {
        return ssTables.isEmpty() || ssTables.size() == 1 && !hasTombstones;
    }

    public interface Data extends Iterable<Entry<MemorySegment>> {
    }

    private static class CleanerThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(final Runnable r) {
            final Thread thread = new Thread(r, "Storage-Cleaner");
            thread.setDaemon(true);
            return thread;
        }
    }
}
