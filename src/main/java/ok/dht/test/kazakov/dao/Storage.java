package ok.dht.test.kazakov.dao;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.Cleaner;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadFactory;

class Storage implements Closeable {

    private static final Cleaner CLEANER = Cleaner.create(new Storage.CleanerThreadFactory());

    private static final Logger LOG = LoggerFactory.getLogger(Storage.class);

    // supposed to have fresh files first

    private final ResourceScope scope;
    private final List<MemorySegment> sstables;
    private final boolean hasTombstones;

    static Storage load(final Config config) throws IOException {
        final Path basePath = config.basePath();
        final Path compactedFile = config.basePath().resolve(StorageHelper.COMPACTED_FILE);
        if (Files.exists(compactedFile)) {
            StorageHelper.finishCompact(config, compactedFile);
        }

        final ArrayList<MemorySegment> sstables = new ArrayList<>();
        final ResourceScope scope = ResourceScope.newSharedScope(CLEANER);

        int i = 0;
        boolean loadedAll = false;
        while (!loadedAll) {
            final Path nextFile = basePath.resolve(StorageHelper.FILE_NAME + i + StorageHelper.FILE_EXT);
            try {
                sstables.add(StorageHelper.mapForRead(scope, nextFile));
            } catch (final NoSuchFileException e) {
                loadedAll = true;
            }
            i++;
        }

        final boolean hasTombstones = !sstables.isEmpty() && MemoryAccess.getLongAtOffset(sstables.get(0), 16) == 1;
        return new Storage(scope, sstables, hasTombstones);
    }

    // it is supposed that entries can not be changed externally during this method call
    static void save(
            final Config config,
            final Storage previousState,
            final Collection<Entry<MemorySegment>> entries) throws IOException {
        final int nextSSTableIndex = previousState.sstables.size();
        final Path sstablePath = config.basePath()
                .resolve(StorageHelper.FILE_NAME + nextSSTableIndex + StorageHelper.FILE_EXT);
        StorageHelper.save(entries::iterator, sstablePath);
    }

    private Storage(final ResourceScope scope, final List<MemorySegment> sstables, final boolean hasTombstones) {
        this.scope = scope;
        this.sstables = sstables;
        this.hasTombstones = hasTombstones;
    }

    private long greaterOrEqualEntryIndex(final MemorySegment sstable, final MemorySegment key) {
        final long index = entryIndex(sstable, key);
        if (index < 0) {
            return ~index;
        }
        return index;
    }

    // file structure:
    // (fileVersion)(entryCount)((entryPosition)...)|((keySize/key/valueSize/value)...)
    private long entryIndex(final MemorySegment sstable, final MemorySegment key) {
        final long fileVersion = MemoryAccess.getLongAtOffset(sstable, 0);
        if (fileVersion != 0) {
            throw new IllegalStateException("Unknown file version: " + fileVersion);
        }
        final long recordsCount = MemoryAccess.getLongAtOffset(sstable, 8);
        if (key == null) {
            return recordsCount;
        }

        long left = 0;
        long right = recordsCount - 1;

        while (left <= right) {
            final long mid = (left + right) >>> 1;

            final long keyPos = MemoryAccess.getLongAtOffset(sstable,
                    StorageHelper.INDEX_HEADER_SIZE + mid * StorageHelper.INDEX_RECORD_SIZE);
            final long keySize = MemoryAccess.getLongAtOffset(sstable, keyPos);

            final MemorySegment keyForCheck = sstable.asSlice(keyPos + Long.BYTES, keySize);
            final int comparedResult = MemorySegmentComparator.INSTANCE.compare(key, keyForCheck);
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

    private Entry<MemorySegment> entryAt(final MemorySegment sstable, final long keyIndex) {
        try {
            final long offset = MemoryAccess.getLongAtOffset(sstable,
                    StorageHelper.INDEX_HEADER_SIZE + keyIndex * StorageHelper.INDEX_RECORD_SIZE);
            final long keySize = MemoryAccess.getLongAtOffset(sstable, offset);
            final long valueOffset = offset + Long.BYTES + keySize;
            final long valueSize = MemoryAccess.getLongAtOffset(sstable, valueOffset);
            return new BaseEntry<>(
                    sstable.asSlice(offset + Long.BYTES, keySize),
                    valueSize == -1 ? null : sstable.asSlice(valueOffset + Long.BYTES, valueSize)
            );
        } catch (final IllegalStateException e) {
            throw checkForClose(e);
        }
    }

    public Entry<MemorySegment> get(final MemorySegment key) {
        try {
            for (int i = sstables.size() - 1; i >= 0; i--) {
                final MemorySegment sstable = sstables.get(i);
                final long keyFromPos = entryIndex(sstable, key);
                if (keyFromPos >= 0) {
                    return entryAt(sstable, keyFromPos);
                }
            }
            return null;
        } catch (final IllegalStateException e) {
            throw checkForClose(e);
        }
    }

    private Iterator<Entry<MemorySegment>> iterate(final MemorySegment sstable,
                                                   final MemorySegment keyFrom,
                                                   final MemorySegment keyTo) {
        final long keyFromPos = greaterOrEqualEntryIndex(sstable, keyFrom);
        final long keyToPos = greaterOrEqualEntryIndex(sstable, keyTo);

        return new Iterator<>() {
            long pos = keyFromPos;

            @Override
            public boolean hasNext() {
                return pos < keyToPos;
            }

            @Override
            public Entry<MemorySegment> next() {
                final Entry<MemorySegment> entry = entryAt(sstable, pos);
                pos++;
                return entry;
            }
        };
    }

    // last is newer
    // it is ok to mutate list after
    public List<Iterator<Entry<MemorySegment>>> iterate(final MemorySegment keyFrom, final MemorySegment keyTo) {
        try {
            final List<Iterator<Entry<MemorySegment>>> iterators = new ArrayList<>(sstables.size());
            for (final MemorySegment sstable : sstables) {
                iterators.add(iterate(sstable, keyFrom, keyTo));
            }
            return iterators;
        } catch (final IllegalStateException e) {
            throw checkForClose(e);
        }
    }

    private RuntimeException checkForClose(final IllegalStateException e) {
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
            } catch (final IllegalStateException e) {
                LOG.error("Could not close storage, trying again...", e);
            }
        }
    }

    public void maybeClose() {
        // no operations
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

    public interface Data {
        Iterator<Entry<MemorySegment>> getIterator() throws IOException;
    }

    private static final class CleanerThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(@Nonnull final Runnable r) {
            final Thread thread = new Thread(r, "Storage-Cleaner");
            thread.setDaemon(true);
            return thread;
        }
    }

}
