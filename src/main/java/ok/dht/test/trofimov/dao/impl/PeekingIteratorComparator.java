package ok.dht.test.trofimov.dao.impl;

import java.util.Comparator;

public final class PeekingIteratorComparator implements Comparator<PeekingIterator> {
    public static final PeekingIteratorComparator INSTANCE = new PeekingIteratorComparator();

    private PeekingIteratorComparator() {
    }

    @Override
    public int compare(PeekingIterator l, PeekingIterator r) {
        if (l.hasNext() && r.hasNext()) {
            int comparing = l.peek().key().compareTo(r.peek().key());
            if (comparing == 0) {
                return Integer.compare(l.getPriority(), r.getPriority());
            }
            return comparing;
        }
        return l.hasNext() ? -1 : 1;
    }
}
