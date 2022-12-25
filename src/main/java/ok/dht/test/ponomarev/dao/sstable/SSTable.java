package ok.dht.test.ponomarev.dao.sstable;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import ok.dht.test.ponomarev.dao.TimestampEntry;
import ok.dht.test.ponomarev.dao.Utils;

public final class SSTable implements Closeable {
    public static final long TOMBSTONE_TAG = -1;
    private static final String SSTABLE_FILE_NAME = "sstable.data";
    private static final String INDEX_FILE_NAME = "sstable.index";

    private final long createdTimeMs;

    private final MemorySegment indexMemorySegment;
    private final MemorySegment tableMemorySegment;

    private SSTable(MemorySegment indexMemorySegment, MemorySegment tableMemorySegment, long createdAt) {
        this.indexMemorySegment = indexMemorySegment;
        this.tableMemorySegment = tableMemorySegment;
        this.createdTimeMs = createdAt;
    }

    public static SSTable createInstance(
        Path path,
        Iterator<TimestampEntry> data,
        long sizeBytes,
        int count,
        long createdAt
    ) throws IOException {
        final Path sstableFile = path.resolve(SSTABLE_FILE_NAME);
        Files.createFile(sstableFile);

        final long sstableSizeBytes = (long) Long.BYTES * 2 * count + sizeBytes;
        final MemorySegment mappedSsTable = MemorySegment.mapFile(
            sstableFile,
            0,
            sstableSizeBytes,
            FileChannel.MapMode.READ_WRITE,
            ResourceScope.newSharedScope()
        );

        final Path indexFile = path.resolve(INDEX_FILE_NAME);
        Files.createFile(indexFile);

        final long indexSizeBytes = (long) Long.BYTES * count;
        final MemorySegment mappedIndex = MemorySegment.mapFile(
            indexFile,
            0,
            indexSizeBytes,
            FileChannel.MapMode.READ_WRITE,
            ResourceScope.newSharedScope()
        );

        flush(data, mappedSsTable, mappedIndex);

        return new SSTable(mappedIndex.asReadOnly(), mappedSsTable.asReadOnly(), createdAt);
    }

    public static SSTable upInstance(Path path, long createdAt) throws IOException {
        final Path sstableFile = path.resolve(SSTABLE_FILE_NAME);
        final Path indexFile = path.resolve(INDEX_FILE_NAME);
        if (Files.notExists(path) || Files.notExists(indexFile)) {
            throw new IllegalArgumentException("Files must exist.");
        }

        final MemorySegment mappedSsTable = MemorySegment.mapFile(
            sstableFile,
            0,
            Files.size(sstableFile),
            FileChannel.MapMode.READ_ONLY,
            ResourceScope.newSharedScope()
        );

        final MemorySegment mappedIndex = MemorySegment.mapFile(
            indexFile,
            0,
            Files.size(indexFile),
            FileChannel.MapMode.READ_ONLY,
            ResourceScope.newSharedScope()
        );

        return new SSTable(mappedIndex, mappedSsTable, createdAt);
    }

    private static void flush(Iterator<TimestampEntry> data, MemorySegment sstable, MemorySegment index) {
        long indexOffset = 0;
        long sstableOffset = 0;
        while (data.hasNext()) {
            MemoryAccess.setLongAtOffset(index, indexOffset, sstableOffset);
            indexOffset += Long.BYTES;

            final TimestampEntry entry = data.next();
            sstableOffset += flush(entry, sstable, sstableOffset);
        }
    }

    @Override
    public void close() {
        indexMemorySegment.scope().close();
        tableMemorySegment.scope().close();
    }

    public Iterator<TimestampEntry> get(MemorySegment from, MemorySegment to) {
        final long size = tableMemorySegment.byteSize();
        if (size == 0) {
            return Collections.emptyIterator();
        }

        if (from == null && to == null) {
            return new MappedIterator(tableMemorySegment);
        }

        final int max = (int) (indexMemorySegment.byteSize() / Long.BYTES) - 1;
        final int fromIndex = from == null ? 0 : Math.abs(findIndexOfKey(from));

        if (fromIndex > max) {
            return Collections.emptyIterator();
        }

        final int toIndex = to == null ? max + 1 : Math.abs(findIndexOfKey(to));
        final long fromPosition = MemoryAccess.getLongAtIndex(indexMemorySegment, fromIndex);
        final long toPosition = toIndex > max ? size : MemoryAccess.getLongAtIndex(indexMemorySegment, toIndex);

        return new MappedIterator(tableMemorySegment.asSlice(fromPosition, toPosition - fromPosition));
    }

    public long getCreatedTime() {
        return createdTimeMs;
    }

    private int findIndexOfKey(MemorySegment key) {
        int low = 0;
        int high = (int) (indexMemorySegment.byteSize() / Long.BYTES) - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;

            final long keyPosition = MemoryAccess.getLongAtIndex(indexMemorySegment, mid);
            final long keySize = MemoryAccess.getLongAtOffset(tableMemorySegment, keyPosition);
            final MemorySegment current = tableMemorySegment.asSlice(keyPosition + Long.BYTES, keySize);

            final int compareResult = Utils.compare(current, key);
            if (compareResult < 0) {
                low = mid + 1;
            } else if (compareResult > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }

        return -low;
    }

    private static long flush(TimestampEntry entry, MemorySegment memorySegment, long offset) {
        final MemorySegment key = entry.key();
        final long keySize = key.byteSize();

        long writeOffset = offset;
        MemoryAccess.setLongAtOffset(memorySegment, writeOffset, keySize);
        writeOffset += Long.BYTES;

        memorySegment.asSlice(writeOffset, keySize).copyFrom(key);
        writeOffset += keySize;

        MemoryAccess.setLongAtOffset(memorySegment, writeOffset, entry.getTimestamp());
        writeOffset += Long.BYTES;

        final MemorySegment value = entry.value();
        if (value == null) {
            MemoryAccess.setLongAtOffset(memorySegment, writeOffset, TOMBSTONE_TAG);
            return writeOffset + Long.BYTES - offset;
        }

        final long valueSize = value.byteSize();
        MemoryAccess.setLongAtOffset(memorySegment, writeOffset, valueSize);
        writeOffset += Long.BYTES;

        memorySegment.asSlice(writeOffset, valueSize).copyFrom(value);
        writeOffset += valueSize;

        return writeOffset - offset;
    }
}
