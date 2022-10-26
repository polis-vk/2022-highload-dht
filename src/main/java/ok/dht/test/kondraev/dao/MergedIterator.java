package ok.dht.test.kondraev.dao;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Function;

final class MergedIterator implements Iterator<MemorySegmentEntry> {
    private final PriorityQueue<Integer> indexes;
    private final List<PeekIterator<MemorySegmentEntry>> iterators;

    private MergedIterator(PriorityQueue<Integer> indexes, List<PeekIterator<MemorySegmentEntry>> iterators) {
        this.indexes = indexes;
        this.iterators = iterators;
    }

    /**
     * Yields entries from multiple iterators of {@link MemorySegmentEntry}. Entries with same keys are merged,
     * leaving one entry from iterator with minimal index.
     *
     * @param iterators which entries are strict ordered by key: key of subsequent entry is strictly greater than
     *                  key of current entry (using {@link MemorySegmentComparator})
     * @return iterator which entries are <em>also</em> strict ordered by key.
     */
    public static MergedIterator of(List<PeekIterator<MemorySegmentEntry>> iterators) {
        Comparator<Integer> indexComparator = Comparator.comparing((Integer i) ->
                iterators.get(i).peek().key, MemorySegmentComparator.INSTANCE).thenComparing(Function.identity());
        final PriorityQueue<Integer> indexes = new PriorityQueue<>(iterators.size(), indexComparator);
        for (int i = 0; i < iterators.size(); i++) {
            if (iterators.get(i).hasNext()) {
                indexes.add(i);
            }
        }
        return new MergedIterator(indexes, iterators);
    }

    @Override
    public boolean hasNext() {
        return !indexes.isEmpty();
    }

    @Override
    public MemorySegmentEntry next() {
        Integer index = indexes.remove();
        PeekIterator<MemorySegmentEntry> iterator = iterators.get(index);
        MemorySegmentEntry entry = iterator.next();
        skipEntriesWithSameKey(entry);
        if (iterator.hasNext()) {
            indexes.offer(index);
        }
        return entry;
    }

    private void skipEntriesWithSameKey(MemorySegmentEntry entry) {
        while (!indexes.isEmpty()) {
            Integer nextIndex = indexes.peek();
            PeekIterator<MemorySegmentEntry> nextIterator = iterators.get(nextIndex);
            if (MemorySegmentComparator.INSTANCE.compare(nextIterator.peek().key, entry.key) != 0) {
                break;
            }
            indexes.remove();
            nextIterator.next();
            if (nextIterator.hasNext()) {
                indexes.offer(nextIndex);
            }
        }
    }
}
