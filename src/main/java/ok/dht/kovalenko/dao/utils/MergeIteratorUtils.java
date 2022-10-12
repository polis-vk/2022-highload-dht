package ok.dht.kovalenko.dao.utils;

import ok.dht.kovalenko.dao.aliases.TypedEntry;
import ok.dht.kovalenko.dao.iterators.PeekIterator;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public final class MergeIteratorUtils {

    public static final Byte NORMAL_VALUE = 1;
    public static final Byte TOMBSTONE_VALUE = -1;

    private MergeIteratorUtils() {
    }

    public static boolean isTombstone(byte b) {
        return b == TOMBSTONE_VALUE;
    }

    public static byte getTombstoneValue(TypedEntry entry) {
        return entry.isTombstone() ? TOMBSTONE_VALUE : NORMAL_VALUE;
    }

    public static void skipEntry(Queue<PeekIterator> iterators, TypedEntry toBeSkipped) {
        List<PeekIterator> toBeRefreshed = new ArrayList<>();
        for (PeekIterator iterator : iterators) {
            if (iterator.hasNext() && DaoUtils.entryComparator.compare(iterator.peek(), toBeSkipped) == 0) {
                iterator.next();
                toBeRefreshed.add(iterator);
            }
        }

        iterators.removeAll(toBeRefreshed);
        for (PeekIterator it : toBeRefreshed) {
            if (it.hasNext()) {
                iterators.add(it);
            }
        }
    }
}
