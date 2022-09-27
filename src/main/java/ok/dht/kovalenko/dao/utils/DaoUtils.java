package ok.dht.kovalenko.dao.utils;

import ok.dht.kovalenko.dao.comparators.ByteBufferComparator;
import ok.dht.kovalenko.dao.comparators.EntryComparator;

import java.nio.ByteBuffer;

public final class DaoUtils {

    public static final ByteBuffer EMPTY_BYTEBUFFER = ByteBuffer.allocate(0);
    public static final EntryComparator entryComparator = EntryComparator.INSTANSE;

    public static final ByteBufferComparator byteBufferComparator = ByteBufferComparator.INSTANSE;

    private DaoUtils() {
    }
}
