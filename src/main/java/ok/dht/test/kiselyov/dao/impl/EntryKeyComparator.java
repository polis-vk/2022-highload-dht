package ok.dht.test.kiselyov.dao.impl;


import java.util.Arrays;
import java.util.Comparator;

public final class EntryKeyComparator implements Comparator<EntryWithTimestamp> {

    public static final Comparator<EntryWithTimestamp> INSTANCE = new EntryKeyComparator();

    private EntryKeyComparator() {

    }

    @Override
    public int compare(EntryWithTimestamp o1, EntryWithTimestamp o2) {
        if (o1.getEntry().key() == o2.getEntry().key()) {
            return Long.compare(o2.getTimestamp(), o1.getTimestamp());
        }
        return Arrays.compare(o1.getEntry().key(), o2.getEntry().key());
    }
}
