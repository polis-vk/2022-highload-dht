package ok.dht.test.kiselyov.dao.impl;

import ok.dht.test.kiselyov.dao.BaseEntry;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class FileIterator implements Iterator<BaseEntry<byte[]>>, Closeable {

    private final FileChannel channelTable;
    private final FileChannel channelIndex;
    private final RandomAccessFile rafTable;
    private final RandomAccessFile rafIndex;
    private long pos;
    private final long to;

    public FileIterator(Path ssTable, Path ssIndex, byte[] from, byte[] to, long indexSize) throws IOException {
        rafTable = new RandomAccessFile(String.valueOf(ssTable), "r");
        rafIndex = new RandomAccessFile(String.valueOf(ssIndex), "r");
        channelTable = rafTable.getChannel();
        channelIndex = rafIndex.getChannel();
        pos = from == null ? 0 : FileOperations.getEntryIndex(channelTable, channelIndex, from, indexSize);
        this.to = to == null ? indexSize : FileOperations.getEntryIndex(channelTable, channelIndex, to, indexSize);
    }

    @Override
    public boolean hasNext() {
        return pos < to;
    }

    @Override
    public BaseEntry<byte[]> next() {
        BaseEntry<byte[]> entry;
        try {
            entry = FileOperations.getCurrent(pos, channelTable, channelIndex);
        } catch (IOException e) {
            throw new NoSuchElementException("There is no next element!", e);
        }
        pos++;
        return entry;
    }

    @Override
    public void close() throws IOException {
        channelTable.close();
        channelIndex.close();
        rafTable.close();
        rafIndex.close();
    }
}
