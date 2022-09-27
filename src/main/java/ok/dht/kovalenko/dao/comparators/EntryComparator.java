package ok.dht.kovalenko.dao.comparators;


import ok.dht.kovalenko.dao.aliases.TypedEntry;
import ok.dht.kovalenko.dao.utils.DaoUtils;

import java.util.Comparator;

public final class EntryComparator
        implements Comparator<TypedEntry> {

    public static final EntryComparator INSTANSE = new EntryComparator();

    private EntryComparator() {
    }

    @Override
    public int compare(TypedEntry e1, TypedEntry e2) {
        return DaoUtils.byteBufferComparator.compare(e1.key(), e2.key());
    }

    public boolean equal(TypedEntry e1, TypedEntry e2) {
        return compare(e1, e2) == 0;
    }

    public boolean lessThan(TypedEntry e1, TypedEntry e2) {
        return compare(e1, e2) < 0;
    }

    public boolean notLessThan(TypedEntry e1, TypedEntry e2) {
        return compare(e1, e2) >= 0;
    }

    public boolean notMoreThan(TypedEntry e1, TypedEntry e2) {
        return compare(e1, e2) <= 0;
    }
}
