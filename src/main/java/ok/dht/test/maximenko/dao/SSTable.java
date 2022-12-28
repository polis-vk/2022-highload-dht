package ok.dht.test.maximenko.dao;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

public class SSTable implements Table {
    private static final long NULL_VALUE_SIZE = -1;
    private static final Comparator<MemorySegment> COMPARATOR = NaturalOrderComparator.getInstance();
    private MemorySegment ssTableFile;

    public SSTable(Path logPath, ResourceScope scope) {
        try {
            long size = Files.size(logPath);
            ssTableFile = MemorySegment.mapFile(logPath, 0, size, FileChannel.MapMode.READ_ONLY, scope);
        } catch (IOException ioException) {
            ssTableFile = null;
        }
    }

    @Override
    public Iterator<Entry<MemorySegment>> get() throws IOException {
        return get(null, null);
    }

    @Override
    public Iterator<Entry<MemorySegment>> get(MemorySegment from, MemorySegment to) throws IOException {
        return new FileEntryIterator(from, to, ssTableFile);
    }

    @Override
    public Iterator<Entry<MemorySegment>> iterator() {
        try {
            return get();
        } catch (IOException e) {
            return Collections.emptyIterator();
        }
    }

    static class FileEntryIterator implements Iterator<Entry<MemorySegment>> {
        private long offset;
        private final MemorySegment log;
        private final MemorySegment last;
        private Entry<MemorySegment> next;
        private final long valuesAmount;

        private static class EntryContainer {
            Entry<MemorySegment> entry;
            long entrySize;

            public EntryContainer(Entry<MemorySegment> entry, long entrySize) {
                this.entry = entry;
                this.entrySize = entrySize;
            }
        }

        private FileEntryIterator(MemorySegment from, MemorySegment last, MemorySegment log) {
            this.log = log;
            if (log.byteSize() > 0) {
                valuesAmount = MemoryAccess.getLongAtOffset(log, 0);
                if (valuesAmount > 0) {
                    offset = getOffsetOfEntryNotLessThan(from);
                    if (offset < 0) {
                        offset = log.byteSize();
                    } else {
                        next = getEntryByOffset();
                    }
                } else {
                    offset = log.byteSize();
                }
            } else {
                valuesAmount = 0;
                offset = log.byteSize();
            }
            this.last = last == null ? null : MemorySegment.ofArray(last.toByteArray());
        }

        @Override
        public boolean hasNext() {
            return next != null && (last == null || COMPARATOR.compare(next.key(), last) < 0);
        }

        @Override
        public Entry<MemorySegment> next() {
            Entry<MemorySegment> result = next;
            EntryContainer nextEntry = getNextEntry();
            offset += nextEntry.entrySize;
            next = nextEntry.entry;
            return result;
        }

        private EntryContainer getNextEntry() {
            Entry<MemorySegment> entry = null;
            long entryOffset = offset;
            if (entryOffset < log.byteSize()) {
                long keySize = MemoryAccess.getLongAtOffset(log, entryOffset);
                entryOffset += Long.BYTES;
                long valueSize = MemoryAccess.getLongAtOffset(log, entryOffset);
                entryOffset += Long.BYTES;

                MemorySegment currentKey = log.asSlice(entryOffset, keySize);
                if (valueSize == NULL_VALUE_SIZE) {
                    entry = new BaseEntry<>(currentKey, null);
                } else {
                    entry = new BaseEntry<>(currentKey, log.asSlice(entryOffset + keySize,
                            valueSize));
                }
                if (valueSize == NULL_VALUE_SIZE) {
                    valueSize = 0;
                }
                entryOffset += keySize + valueSize;

            }

            return new EntryContainer(entry, entryOffset - offset);
        }

        private long getOffsetOfEntryNotLessThan(MemorySegment other) {
            long low = 0;
            long high = valuesAmount - 1;

            long result = -1;
            while (low <= high) {
                long mid = (low + high) >>> 1;
                MemorySegment midVal = getKeyByIndex(mid);
                int cmp = COMPARATOR.compare(midVal, other);

                if (cmp < 0) {
                    low = mid + 1;
                } else if (cmp > 0) {
                    high = mid - 1;
                    result = mid;
                } else {
                    return getEntryOffsetByIndex(mid);
                }
            }

            return result == - 1 ? result : getEntryOffsetByIndex(result);
        }

        private MemorySegment getKeyByIndex(long index) {
            long entryOffset = getEntryOffsetByIndex(index);

            long keySize = MemoryAccess.getLongAtOffset(log, entryOffset);
            long keyOffset = entryOffset + 2L * Long.BYTES;
            return log.asSlice(keyOffset, keySize);
        }

        private Entry<MemorySegment> getEntryByOffset() {
            long keySize = MemoryAccess.getLongAtOffset(log, offset);
            long valueSize = MemoryAccess.getLongAtOffset(log, offset + Long.BYTES);

            long keyOffset = offset + 2L * Long.BYTES;
            MemorySegment key = log.asSlice(keyOffset, keySize);

            long valueOffset = keyOffset + keySize;
            if (valueSize == NULL_VALUE_SIZE) {
                offset = valueOffset;
                return new BaseEntry<>(key, null);
            }

            MemorySegment value = log.asSlice(valueOffset, valueSize);

            offset = valueOffset + valueSize;
            return new BaseEntry<>(key, value);
        }

        private long getEntryOffsetByIndex(long index) {
            long indexOffset = Long.BYTES + index * Long.BYTES;
            return MemoryAccess.getLongAtOffset(log, indexOffset);
        }
    }
}
