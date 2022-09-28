package ok.dht.test.komissarov.database.iterators;

import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import ok.dht.test.komissarov.database.models.Entry;
import ok.dht.test.komissarov.database.utils.StorageUtils;

import java.util.Iterator;

public class IntervalIterator implements Iterator<Entry<MemorySegment>> {

    private final long last;
    private final MemorySegment sstable;
    private final ResourceScope scope;
    private long pos;

    public IntervalIterator(MemorySegment sstable, long keyFromPos, long keyToPos, ResourceScope scope) {
        this.sstable = sstable;
        this.scope = scope;
        pos = keyFromPos;
        last = keyToPos;
    }

    @Override
    public boolean hasNext() {
        return pos < last;
    }

    @Override
    public Entry<MemorySegment> next() {
        Entry<MemorySegment> entry = StorageUtils.entryAt(sstable, pos, scope);
        pos++;
        return entry;
    }
}
