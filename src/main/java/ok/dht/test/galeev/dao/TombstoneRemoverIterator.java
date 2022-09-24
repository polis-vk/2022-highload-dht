package ok.dht.test.galeev.dao;

import jdk.incubator.foreign.MemorySegment;

import java.util.Iterator;

class TombstoneRemoverIterator implements Iterator<Entry<MemorySegment>> {

    private final PriorityPeekingIterator<Entry<MemorySegment>> peekingIterator;

    public TombstoneRemoverIterator(PriorityPeekingIterator<Entry<MemorySegment>> peekingIterator) {
        this.peekingIterator = peekingIterator;
    }

    @Override
    public boolean hasNext() {
        deleteNullEntries();
        return peekingIterator.hasNext();
    }

    @Override
    public Entry<MemorySegment> next() {
        deleteNullEntries();
        return peekingIterator.next();
    }

    void deleteNullEntries() {
        while (peekingIterator.hasNext() && peekingIterator.peek().value() == null) {
            peekingIterator.next();
        }
    }
}
