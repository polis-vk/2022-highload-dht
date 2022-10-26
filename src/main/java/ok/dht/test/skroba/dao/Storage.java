package ok.dht.test.skroba.dao;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import ok.dht.test.skroba.dao.base.BaseEntry;
import ok.dht.test.skroba.dao.base.Entry;
import ok.dht.test.skroba.dao.comparators.MemorySegmentComparator;
import ok.dht.test.skroba.dao.exceptions.StorageClosedException;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static ok.dht.test.skroba.dao.StorageUtils.INDEX_HEADER_SIZE;
import static ok.dht.test.skroba.dao.StorageUtils.INDEX_RECORD_SIZE;

class Storage implements Closeable {
    final List<MemorySegment> sstables;
    private final ResourceScope scope;
    private final boolean hasTombstones;
    
    Storage(ResourceScope scope, List<MemorySegment> sstables, boolean hasTombstones) {
        this.scope = scope;
        this.sstables = sstables;
        this.hasTombstones = hasTombstones;
    }
    
    private long greaterOrEqualEntryIndex(MemorySegment sstable, MemorySegment key) {
        long index = entryIndex(sstable, key);
        if (index < 0) {
            return ~index;
        }
        return index;
    }
    
    // file structure:
    // (fileVersion)(entryCount)((entryPosition)...)|((keySize/key/valueSize/value)...)
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
            long valueOffset = offset + Long.BYTES + keySize;
            long valueSize = MemoryAccess.getLongAtOffset(sstable, valueOffset);
            return new BaseEntry<>(
                    sstable.asSlice(offset + Long.BYTES, keySize),
                    valueSize == -1 ? null : sstable.asSlice(valueOffset + Long.BYTES, valueSize)
            );
        } catch (IllegalStateException e) {
            throw checkForClose(e);
        }
    }
    
    public Entry<MemorySegment> get(MemorySegment key) {
        try {
            for (int i = sstables.size() - 1; i >= 0; i--) {
                MemorySegment sstable = sstables.get(i);
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
    
    // last is newer
    // it is ok to mutate list after
    public List<Iterator<Entry<MemorySegment>>> iterate(MemorySegment keyFrom, MemorySegment keyTo) {
        try {
            ArrayList<Iterator<Entry<MemorySegment>>> iterators = new ArrayList<>(sstables.size());
            for (MemorySegment sstable : sstables) {
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
                // Do nothing
            }
        }
    }
    
    public void maybeClose() {
        // Nothing
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
        Iterator<Entry<MemorySegment>> iterator() throws IOException;
    }
    
}
