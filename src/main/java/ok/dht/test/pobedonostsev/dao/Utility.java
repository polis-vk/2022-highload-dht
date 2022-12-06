package ok.dht.test.pobedonostsev.dao;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;

public final class Utility {

    private Utility() {
    }

    public static long entryIndex(MemorySegment sstable, MemorySegment key) {
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
            long keyPos =
                    MemoryAccess.getLongAtOffset(sstable, Storage.INDEX_HEADER_SIZE + mid * Storage.INDEX_RECORD_SIZE);
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

    public static boolean checkRange(MemorySegment sstable, MemorySegment key) {
        long fileVersion = MemoryAccess.getLongAtOffset(sstable, 0);
        if (fileVersion != 0) {
            throw new IllegalStateException("Unknown file version: " + fileVersion);
        }
        long recordsCount = MemoryAccess.getLongAtOffset(sstable, 8);
        // Smallest key
        long keyPos = MemoryAccess.getLongAtOffset(sstable, Storage.INDEX_HEADER_SIZE);
        long keySize = MemoryAccess.getLongAtOffset(sstable, keyPos);
        MemorySegment keyForCheck = sstable.asSlice(keyPos + Long.BYTES, keySize);
        int comparedResult = MemorySegmentComparator.INSTANCE.compare(key, keyForCheck);
        if (comparedResult < 0) {
            return false;
        }
        // Biggest key
        keyPos = MemoryAccess.getLongAtOffset(sstable,
                Storage.INDEX_HEADER_SIZE + (recordsCount - 1) * Storage.INDEX_RECORD_SIZE);
        keySize = MemoryAccess.getLongAtOffset(sstable, keyPos);
        keyForCheck = sstable.asSlice(keyPos + Long.BYTES, keySize);
        comparedResult = MemorySegmentComparator.INSTANCE.compare(key, keyForCheck);
        return comparedResult <= 0;
    }
}
