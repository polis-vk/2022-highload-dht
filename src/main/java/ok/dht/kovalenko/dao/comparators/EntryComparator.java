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
}
