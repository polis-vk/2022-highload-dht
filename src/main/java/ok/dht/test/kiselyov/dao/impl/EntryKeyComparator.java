package ok.dht.test.kiselyov.dao.impl;

import ok.dht.test.kiselyov.dao.BaseEntry;

import java.util.Arrays;
import java.util.Comparator;

public final class EntryKeyComparator implements Comparator<BaseEntry<byte[]>> {

    public static final Comparator<BaseEntry<byte[]>> INSTANCE = new EntryKeyComparator();

    private EntryKeyComparator() {

    }

    @Override
    public int compare(BaseEntry<byte[]> o1, BaseEntry<byte[]> o2) {
        return Arrays.compare(o1.key(), o2.key());
    }
}
