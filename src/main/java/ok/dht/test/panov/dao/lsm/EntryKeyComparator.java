package ok.dht.test.panov.dao.lsm;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.panov.dao.Entry;

import java.util.Comparator;

public class EntryKeyComparator implements Comparator<Entry<MemorySegment>> {

    public static final Comparator<Entry<MemorySegment>> INSTANCE = new EntryKeyComparator();

    private EntryKeyComparator() {
    }

    @Override
    public int compare(Entry<MemorySegment> o1, Entry<MemorySegment> o2) {
        return MemorySegmentComparator.INSTANCE.compare(o1.key(), o2.key());
    }
}
