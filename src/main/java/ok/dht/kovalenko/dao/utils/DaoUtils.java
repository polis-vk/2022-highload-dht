package ok.dht.kovalenko.dao.utils;

import ok.dht.kovalenko.dao.aliases.MemorySSTableStorage;
import ok.dht.kovalenko.dao.comparators.EntryComparator;

import java.nio.ByteBuffer;

public final class DaoUtils {

    public static final ByteBuffer EMPTY_BYTEBUFFER = ByteBuffer.allocate(0);
    public static final EntryComparator entryComparator = EntryComparator.INSTANSE;

    private DaoUtils() {
    }

    public static boolean isEmpty(MemorySSTableStorage sstablesForWrite) {
        return sstablesForWrite.isEmpty() || sstablesForWrite.peek().isEmpty();
    }
}
