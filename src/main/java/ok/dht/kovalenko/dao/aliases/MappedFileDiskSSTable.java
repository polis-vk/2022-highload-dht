package ok.dht.kovalenko.dao.aliases;

import ok.dht.kovalenko.dao.Serializer;
import ok.dht.kovalenko.dao.dto.ByteBufferRange;
import ok.dht.kovalenko.dao.dto.MappedPairedFiles;
import ok.dht.kovalenko.dao.utils.DaoUtils;
import ok.dht.kovalenko.dao.utils.FileUtils;

import java.nio.ByteBuffer;

public class MappedFileDiskSSTable
        extends DiskSSTable<MappedPairedFiles> {

    private final Serializer serializer;

    public MappedFileDiskSSTable(long key, MappedPairedFiles value, Serializer serializer) {
        super(key, value);
        this.serializer = serializer;
    }

    public TypedEntry get(ByteBuffer key) {
        ByteBufferRange range = this.value.range();
        if (DaoUtils.byteBufferComparator.lessThan(key, range.from())
                || DaoUtils.byteBufferComparator.greaterThan(key, range.to())) {
            return null;
        }
        TypedEntry res = null;
        int greaterOrEqualEntryIndex = entryIndex(this.value, key);
        if (greaterOrEqualEntryIndex >= 0) {
            res = entryAt(this.value, greaterOrEqualEntryIndex);
        }
        return res;
    }

    public TypedIterator get(ByteBuffer from, ByteBuffer to) {
        ByteBufferRange range = this.value.range();
        if (DaoUtils.byteBufferComparator.lessThan(range.to(), from)
                || DaoUtils.byteBufferComparator.greaterThan(range.from(), to)) {
            return null;
        }
        int fromPos = greaterOrEqualEntryIndex(this.value, from);
        int toPos = greaterOrEqualEntryIndex(this.value, to);
        MappedPairedFiles mappedPairedFiles = this.value;
        return new TypedIterator() {
            int curPos = fromPos;

            @Override
            public boolean hasNext() {
                return curPos < toPos;
            }

            @Override
            public TypedEntry next() {
                return entryAt(mappedPairedFiles, curPos++);
            }
        };
    }

    private int entryIndex(MappedPairedFiles mappedPairedFiles, ByteBuffer key) {
        int a = 0;
        int b = mappedPairedFiles.indexesLimit() / FileUtils.INDEX_SIZE;
        if (key == null) {
            return b;
        }

        while (a < b) {
            int c = (b + a) / 2;
            ByteBuffer keyForCompare = serializer.readKey(mappedPairedFiles, c * FileUtils.INDEX_SIZE);
            int compare = key.rewind().compareTo(keyForCompare.rewind());
            if (compare > 0) {
                a = c + 1;
            } else if (compare == 0) {
                return c;
            } else {
                b = c;
            }
        }

        return ~a;
    }

    private int greaterOrEqualEntryIndex(MappedPairedFiles mappedPairedFiles, ByteBuffer key) {
        int index = entryIndex(mappedPairedFiles, key);
        if (index < 0) {
            return ~index;
        }
        return index;
    }

    private TypedEntry entryAt(MappedPairedFiles mappedPairedFiles, int pos) {
        return serializer.readEntry(mappedPairedFiles, FileUtils.INDEX_SIZE * pos);
    }
}
