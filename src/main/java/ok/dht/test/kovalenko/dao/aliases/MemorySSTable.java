package ok.dht.test.kovalenko.dao.aliases;

import ok.dht.test.kovalenko.dao.utils.DaoUtils;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.concurrent.ConcurrentSkipListMap;

public class MemorySSTable
        extends ConcurrentSkipListMap<ByteBuffer, TypedTimedEntry> {

    // See the key-value pairs' writing format
    public static int sizeOf(TypedTimedEntry entry) {
        int size = 0;
        size += DaoUtils.TIMESTAMP_LENGTH; // timestamp
        size += 1; // tombstoneFlag
        size += Integer.BYTES + entry.key().rewind().remaining(); // key
        if (!entry.isTombstone()) {
            size += Integer.BYTES + entry.value().rewind().remaining(); // value
        }
        return size;
    }

    public Iterator<TypedTimedEntry> get(ByteBuffer from, ByteBuffer to) {
        Iterator<TypedTimedEntry> rangeIt;
        if (to == null) {
            rangeIt = this.tailMap(from).values().iterator();
        } else {
            rangeIt = this.subMap(from, to).values().iterator();
        }
        return rangeIt.hasNext() ? rangeIt : null;
    }
}
