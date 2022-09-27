package ok.dht.kovalenko.dao.comparators;

import java.nio.ByteBuffer;
import java.util.Comparator;

public final class ByteBufferComparator
        implements Comparator<ByteBuffer> {

    public static final ByteBufferComparator INSTANSE = new ByteBufferComparator();

    private ByteBufferComparator() {
    }

    @Override
    public int compare(ByteBuffer b1, ByteBuffer b2) {
        if (b2 == null) {
            return -1; // null is greater than anything
        }
        return b1.rewind().compareTo(b2.rewind());
    }

    public boolean lessThan(ByteBuffer b1, ByteBuffer b2) {
        return compare(b1, b2) < 0;
    }

    public boolean greaterThan(ByteBuffer b1, ByteBuffer b2) {
        return compare(b1, b2) > 0;
    }
}
