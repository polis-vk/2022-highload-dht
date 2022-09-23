package ok.dht.kovalenko.dao.iterators;

import ok.dht.kovalenko.dao.Serializer;
import ok.dht.kovalenko.dao.aliases.TypedBaseEntry;
import ok.dht.kovalenko.dao.aliases.TypedEntry;
import ok.dht.kovalenko.dao.aliases.TypedIterator;
import ok.dht.kovalenko.dao.dto.MappedPairedFiles;
import ok.dht.kovalenko.dao.utils.DaoUtils;
import ok.dht.kovalenko.dao.utils.FileUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

public class FileIterator implements TypedIterator {

    private final MappedPairedFiles mappedPairedFiles;
    private final TypedEntry from;
    private final TypedEntry to;
    private final Serializer serializer;
    private int curIndexesPos;

    public FileIterator(MappedPairedFiles mappedPairedFiles, Serializer serializer, ByteBuffer from, ByteBuffer to)
            throws IOException {
        this.mappedPairedFiles = mappedPairedFiles;
        this.from = new TypedBaseEntry(from, from);
        this.to = new TypedBaseEntry(to, to);
        this.serializer = serializer;

        if (dataExists() && boundNotReached()) {
            binarySearchInFile();
        }
    }

    @Override
    public boolean hasNext() {
        try {
            return dataExists() && isInBounds();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public TypedEntry next() {
        if (!hasNext()) {
            throw new RuntimeException("There is no next iterable element");
        }

        try {
            TypedEntry peek = peek();
            this.curIndexesPos += FileUtils.INDEX_SIZE;
            return peek;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private TypedEntry peek() throws IOException {
        return EOFNotReached()
                ? serializer.readEntry(mappedPairedFiles, curIndexesPos)
                : null;
    }

    private boolean dataExists() throws IOException {
        return peek() != null;
    }

    private boolean isInBounds() throws IOException {
        TypedEntry peek = peek();
        return peek != null
                && isInBoundsToExclude(peek, from, to);
    }

    private boolean EOFNotReached() {
        return curIndexesPos < mappedPairedFiles.indexesLimit();
    }

    private void binarySearchInFile() {
        int a = 0;
        int b = getIndexesFileLength() / FileUtils.INDEX_SIZE;
        TypedEntry ceilEntry = last();

        while (a < b) {
            int c = (b + a) / 2;
            TypedEntry curEntry = serializer.readEntry(mappedPairedFiles, c * FileUtils.INDEX_SIZE);
            if (isInBoundsToInclude(curEntry, from, ceilEntry)) {
                ceilEntry = curEntry;
                this.curIndexesPos = c * FileUtils.INDEX_SIZE;
            }

            int curEntryCompareToFrom = DaoUtils.entryComparator.compare(curEntry, from);
            if (curEntryCompareToFrom < 0) {
                a = c + 1;
            } else if (curEntryCompareToFrom == 0) {
                break;
            } else {
                b = c;
            }
        }
    }

    private TypedEntry last() {
        return serializer.readEntry(mappedPairedFiles, getIndexesFileLength() - FileUtils.INDEX_SIZE);
    }

    private boolean boundNotReached() {
        return DaoUtils.entryComparator.notMoreThan(from, last());
    }

    private int getIndexesFileLength() {
        return mappedPairedFiles.indexesFile().limit();
    }

    private boolean isInBoundsToInclude(TypedEntry toBeChecked, TypedEntry from, TypedEntry to) {
        return DaoUtils.entryComparator.notLessThan(toBeChecked, from)
                && (to.key() == null || DaoUtils.entryComparator.notMoreThan(toBeChecked, to));
    }

    private boolean isInBoundsToExclude(TypedEntry toBeChecked, TypedEntry from, TypedEntry to) {
        return DaoUtils.entryComparator.notLessThan(toBeChecked, from)
                && (to.key() == null || DaoUtils.entryComparator.lessThan(toBeChecked, to));
    }
}
