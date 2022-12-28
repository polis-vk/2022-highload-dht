package ok.dht.test.kovalenko.dao.utils;

import ok.dht.test.kovalenko.dao.comparators.ByteBufferComparator;
import ok.dht.test.kovalenko.dao.comparators.EntryComparator;

import java.nio.ByteBuffer;

public final class DaoUtils {

    public static final ByteBuffer EMPTY_BYTEBUFFER = ByteBuffer.allocate(0);
    public static final EntryComparator entryComparator = EntryComparator.INSTANSE;

    public static final ByteBufferComparator byteBufferComparator = ByteBufferComparator.INSTANSE;
    public static final int TIMESTAMP_LENGTH = Long.BYTES;

    private DaoUtils() {
    }
}
