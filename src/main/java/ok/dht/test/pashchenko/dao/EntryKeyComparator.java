package ok.dht.test.pashchenko.dao;

import java.util.Comparator;

public class EntryKeyComparator implements Comparator<Entry> {

    public static final Comparator<Entry> INSTANCE = new EntryKeyComparator();

    private EntryKeyComparator() {
    }

    @Override
    public int compare(Entry o1, Entry o2) {
        return MemorySegmentComparator.INSTANCE.compare(o1.key(), o2.key());
    }
}
