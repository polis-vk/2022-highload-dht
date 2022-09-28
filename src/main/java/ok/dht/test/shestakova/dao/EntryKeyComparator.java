package ok.dht.test.shestakova.dao;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.shestakova.dao.baseElements.BaseEntry;

import java.util.Comparator;

public final class EntryKeyComparator implements Comparator<BaseEntry<MemorySegment>> {

    public static final Comparator<BaseEntry<MemorySegment>> INSTANCE = new EntryKeyComparator();

    private EntryKeyComparator() {
    }

    @Override
    public int compare(BaseEntry<MemorySegment> o1, BaseEntry<MemorySegment> o2) {
        return MemorySegmentComparator.INSTANCE.compare(o1.key(), o2.key());
    }
}
