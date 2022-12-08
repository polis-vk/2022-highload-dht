package ok.dht.test.komissarov.database.utils;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import ok.dht.test.komissarov.database.exceptions.StorageClosedException;
import ok.dht.test.komissarov.database.models.BaseEntry;
import ok.dht.test.komissarov.database.models.Entry;

import java.lang.ref.Cleaner;
import java.util.concurrent.ThreadFactory;

public final class StorageUtils {

    private static final int INDEX_HEADER_SIZE = Long.BYTES * 3;
    private static final int INDEX_RECORD_SIZE = Long.BYTES;
    private static final Cleaner CLEANER = Cleaner.create(new CustomThreadFactory());

    private StorageUtils() {

    }

    public static long getSize(Entry<MemorySegment> entry) {
        if (entry.value() == null) {
            return Long.BYTES + entry.key().byteSize() + 2 * Long.BYTES;
        } else {
            return Long.BYTES + entry.value().byteSize() + entry.key().byteSize() + 2 * Long.BYTES;
        }
    }

    public static Cleaner getCleaner() {
        return CLEANER;
    }

    public static Entry<MemorySegment> entryAt(MemorySegment sstable, long keyIndex, ResourceScope scope) {
        try {
            long offset = MemoryAccess.getLongAtOffset(sstable, INDEX_HEADER_SIZE + keyIndex * INDEX_RECORD_SIZE);
            long keySize = MemoryAccess.getLongAtOffset(sstable, offset);
            long valueOffset = offset + 2 * Long.BYTES + keySize;
            long valueSize = MemoryAccess.getLongAtOffset(sstable, valueOffset);
            return new BaseEntry<>(
                    sstable.asSlice(offset + Long.BYTES, keySize),
                    valueSize == -1 ? null : sstable.asSlice(valueOffset + Long.BYTES, valueSize),
                    MemoryAccess.getLongAtOffset(sstable, offset + Long.BYTES + keySize)
            );
        } catch (IllegalStateException e) {
            throw checkForClose(e, scope);
        }
    }

    public static RuntimeException checkForClose(IllegalStateException e, ResourceScope scope) {
        if (scope.isAlive()) {
            throw e;
        }
        throw new StorageClosedException(e);
    }

    private static class CustomThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "Storage-Cleaner") {
                @Override
                public synchronized void start() {
                    setDaemon(true);
                    super.start();
                }
            };
        }
    }

}
