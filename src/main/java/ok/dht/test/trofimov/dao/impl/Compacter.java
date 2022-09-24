package ok.dht.test.trofimov.dao.impl;

import ok.dht.test.trofimov.dao.Config;
import ok.dht.test.trofimov.dao.Entry;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import static ok.dht.test.trofimov.dao.impl.InMemoryDao.ALL_FILES;
import static ok.dht.test.trofimov.dao.impl.InMemoryDao.DATA_EXT;
import static ok.dht.test.trofimov.dao.impl.InMemoryDao.INDEX_EXT;

public class Compacter implements Runnable {
    private final Config config;
    private final AtomicReference<Deque<String>> filesList;
    private final InMemoryDao inMemoryDao;
    private final Lock filesLock;

    public Compacter(Config config, AtomicReference<Deque<String>> filesList, InMemoryDao inMemoryDao,
                     Lock filesLock) {
        this.config = config;
        this.filesList = filesList;
        this.inMemoryDao = inMemoryDao;
        this.filesLock = filesLock;
    }

    @Override
    public void run() {
        String name = Utils.getUniqueFileName(filesList.get());
        Path basePath = config.basePath();
        Path file = basePath.resolve(name + DATA_EXT);
        Path index = basePath.resolve(name + INDEX_EXT);
        try (RandomAccessFile output = new RandomAccessFile(file.toString(), "rw");
             RandomAccessFile indexOut = new RandomAccessFile(index.toString(), "rw");
             RandomAccessFile allFilesOut = new RandomAccessFile(basePath.resolve(ALL_FILES).toString(), "rw")
        ) {
            Pair<Deque<String>, List<PeekingIterator>> files =
                    inMemoryDao.getFilePeekingIteratorList(null, null, 0);
            Iterator<Entry<String>> iterator = new MergeIterator(files.second);
            output.seek(Integer.BYTES);
            int count = 0;
            while (iterator.hasNext()) {
                Entry<String> entry = iterator.next();
                count++;
                if (entry != null) {
                    indexOut.writeLong(output.getFilePointer());
                    Utils.writeEntry(output, entry);
                }
            }
            output.seek(0);
            output.writeInt(count);
            filesLock.lock();
            try {
                filesList.get().removeAll(files.first);
                filesList.get().add(name);
            } finally {
                filesLock.unlock();
            }
            Utils.writeFileListToDisk(filesList, allFilesOut);
            Utils.removeOldFiles(config, files.first);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
