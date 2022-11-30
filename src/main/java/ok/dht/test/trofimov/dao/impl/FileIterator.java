package ok.dht.test.trofimov.dao.impl;

import ok.dht.test.trofimov.dao.Entry;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class FileIterator implements Iterator<Entry<String>> {
    private final String to;
    private final RandomAccessFile raf;
    private Entry<String> nextEntry;
    private final long fileLength;

    public FileIterator(Path basePath, FileInfo file, String from, String to) throws IOException {
        this.to = to;
        raf = new RandomAccessFile(basePath.resolve(file.filename() + InMemoryDao.DATA_EXT).toString(), "r");
        fileLength = raf.length();
        nextEntry = Utils.findCeilEntry(raf, from, basePath.resolve(file.filename() + InMemoryDao.INDEX_EXT));
    }

    @Override
    public boolean hasNext() {
        boolean hasNext;
        try {
            hasNext = (to == null && nextEntry != null) || (nextEntry != null && nextEntry.key().compareTo(to) < 0);
            if (!hasNext) {
                raf.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return hasNext;
    }

    @Override
    public Entry<String> next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        Entry<String> retval = nextEntry;
        try {
            if (raf.getFilePointer() < fileLength) {
                nextEntry = Utils.readEntry(raf);
            } else {
                nextEntry = null;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return retval;
    }
}
