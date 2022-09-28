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
    private static final Cleaner CLEANER = Cleaner.create(new ThreadFactory() {
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
    });

    private StorageUtils() {

    }

    public static long getSize(Entry<MemorySegment> entry) {
        if (entry.value() == null) {
            return Long.BYTES + entry.key().byteSize() + Long.BYTES;
        } else {
            return Long.BYTES + entry.value().byteSize() + entry.key().byteSize() + Long.BYTES;
        }
    }

    public static Cleaner getCleaner() {
        return CLEANER;
    }

    public static Entry<MemorySegment> entryAt(MemorySegment sstable, long keyIndex, ResourceScope scope) {
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
            throw checkForClose(e, scope);
        }
    }

    public static RuntimeException checkForClose(IllegalStateException e, ResourceScope scope) {
        if (!scope.isAlive()) {
            throw new StorageClosedException(e);
        } else {
            throw e;
        }
    }

}
