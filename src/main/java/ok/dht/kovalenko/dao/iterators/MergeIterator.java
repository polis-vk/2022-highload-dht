package ok.dht.kovalenko.dao.iterators;

import ok.dht.kovalenko.dao.aliases.TypedEntry;
import ok.dht.kovalenko.dao.aliases.TypedIterator;
import ok.dht.kovalenko.dao.comparators.IteratorComparator;
import ok.dht.kovalenko.dao.utils.MergeIteratorUtils;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

public class MergeIterator
        implements TypedIterator {

    private final Queue<PeekIterator> iterators = new PriorityQueue<>(IteratorComparator.INSTANSE);
    private final TombstoneFilteringIterator tombstoneFilteringIterator;

    public MergeIterator(List<Iterator<TypedEntry>> memoryIterators, List<Iterator<TypedEntry>> diskIterators)
            throws IOException, ReflectiveOperationException {
        memoryIterators.forEach(it -> iterators.add(new PeekIterator(it, iterators.size())));
        diskIterators.forEach(it -> iterators.add(new PeekIterator(it, iterators.size())));
        this.tombstoneFilteringIterator = new TombstoneFilteringIterator(iterators);
    }

    @Override
    public boolean hasNext() {
        return tombstoneFilteringIterator.hasNext();
    }

    @Override
    public TypedEntry next() {
        TypedEntry result = tombstoneFilteringIterator.next();
        MergeIteratorUtils.skipEntry(iterators, result);
        return result;
    }
}
