package ok.dht.kovalenko.dao.iterators;

import ok.dht.ServiceConfig;
import ok.dht.kovalenko.dao.Serializer;
import ok.dht.kovalenko.dao.aliases.MemorySSTable;
import ok.dht.kovalenko.dao.aliases.MemorySSTableStorage;
import ok.dht.kovalenko.dao.aliases.TypedEntry;
import ok.dht.kovalenko.dao.aliases.TypedIterator;
import ok.dht.kovalenko.dao.comparators.IteratorComparator;
import ok.dht.kovalenko.dao.dto.MappedPairedFiles;
import ok.dht.kovalenko.dao.utils.DaoUtils;
import ok.dht.kovalenko.dao.utils.FileUtils;
import ok.dht.kovalenko.dao.utils.MergeIteratorUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.PriorityQueue;
import java.util.Queue;

public class MergeIterator
        implements TypedIterator {

    private final Queue<PeekIterator> iterators = new PriorityQueue<>(IteratorComparator.INSTANSE);
    private final TombstoneFilteringIterator tombstoneFilteringIterator;

    public MergeIterator(ByteBuffer from, ByteBuffer to, Serializer serializer,
                         ServiceConfig config, MemorySSTableStorage memorySSTables)
            throws IOException, ReflectiveOperationException {
        ByteBuffer from1 = from == null ? DaoUtils.EMPTY_BYTEBUFFER : from;
        int priority = 0;

        for (MemorySSTable memorySSTable : memorySSTables) {
            if (memorySSTable.isEmpty()) {
                continue;
            }

            if (to == null) {
                iterators.add(new PeekIterator(memorySSTable.tailMap(from1).values().iterator(), priority++));
            } else {
                iterators.add(new PeekIterator(memorySSTable.subMap(from1, to).values().iterator(), priority++));
            }
        }

        long sstablesSize = FileUtils.nFiles(config) / 2;
        for (; priority <= sstablesSize + memorySSTables.size() - 1; ++priority) {
            MappedPairedFiles mappedPairedFiles = serializer.get(sstablesSize + memorySSTables.size() - priority);
            iterators.add(new PeekIterator(
                    new FileIterator(mappedPairedFiles, serializer, from1, to),
                    priority
            ));
        }

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
