package ok.dht.kovalenko.dao.aliases;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.concurrent.ConcurrentSkipListMap;

public class MemorySSTable
        extends ConcurrentSkipListMap<ByteBuffer, TypedEntry> {

    // See the key-value pairs' writing format
    public static int sizeOf(TypedEntry entry) {
        int size = 1 + Integer.BYTES + entry.key().rewind().remaining();
        if (!entry.isTombstone()) {
            size += Integer.BYTES + entry.value().rewind().remaining();
        }
        return size;
    }

    public Iterator<TypedEntry> get(ByteBuffer from, ByteBuffer to) {
        Iterator<TypedEntry> rangeIt;
        if (to == null) {
            rangeIt = this.tailMap(from).values().iterator();
        } else {
            rangeIt = this.subMap(from, to).values().iterator();
        }
        return rangeIt.hasNext() ? rangeIt : null;
    }
}
