package ok.dht.test.galeev.dao.iterators;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.galeev.dao.entry.Entry;

import java.util.Iterator;

public class TombstoneRemoverIterator implements Iterator<Entry<MemorySegment, MemorySegment>> {

    private final PriorityPeekingIterator<Entry<MemorySegment, MemorySegment>> peekingIterator;

    public TombstoneRemoverIterator(PriorityPeekingIterator<Entry<MemorySegment, MemorySegment>> peekingIterator) {
        this.peekingIterator = peekingIterator;
    }

    @Override
    public boolean hasNext() {
        deleteNullEntries();
        return peekingIterator.hasNext();
    }

    @Override
    public Entry<MemorySegment, MemorySegment> next() {
        deleteNullEntries();
        return peekingIterator.next();
    }

    void deleteNullEntries() {
        while (peekingIterator.hasNext() && peekingIterator.peek().value() == null) {
            peekingIterator.next();
        }
    }
}
