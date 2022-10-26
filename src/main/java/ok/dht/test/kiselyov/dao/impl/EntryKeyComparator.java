package ok.dht.test.kiselyov.dao.impl;


import ok.dht.test.kiselyov.dao.BaseEntry;

import java.util.Arrays;
import java.util.Comparator;

public final class EntryKeyComparator implements Comparator<BaseEntry<byte[], Long>> {

    public static final Comparator<BaseEntry<byte[], Long>> INSTANCE = new EntryKeyComparator();

    private EntryKeyComparator() {

    }

    @Override
    public int compare(BaseEntry<byte[], Long> o1, BaseEntry<byte[], Long> o2) {
        if (o1.key() == o2.key()) {
            return Long.compare(o2.timestamp(), o1.timestamp());
        }
        return Arrays.compare(o1.key(), o2.key());
    }
}
