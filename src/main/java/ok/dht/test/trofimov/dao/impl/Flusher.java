package ok.dht.test.trofimov.dao.impl;

import ok.dht.test.trofimov.dao.Config;
import ok.dht.test.trofimov.dao.Entry;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import static ok.dht.test.trofimov.dao.impl.InMemoryDao.ALL_FILES;
import static ok.dht.test.trofimov.dao.impl.InMemoryDao.DATA_EXT;
import static ok.dht.test.trofimov.dao.impl.InMemoryDao.INDEX_EXT;

public class Flusher implements Runnable {
    private final Config config;
    private final BlockingDeque<ConcurrentNavigableMap<String, Entry<String>>> queueToFlush;
    private final AtomicReference<Deque<FileInfo>> filesList;
    private final Lock filesLock;

    public Flusher(Config config, BlockingDeque<ConcurrentNavigableMap<String, Entry<String>>> queueToFlush,
                   AtomicReference<Deque<FileInfo>> filesList, Lock filesLock) {
        this.config = config;
        this.queueToFlush = queueToFlush;
        this.filesList = filesList;
        this.filesLock = filesLock;
    }

    @Override
    public void run() {
        String catalog = config.basePath().resolve(ALL_FILES).toString();
        while (!Thread.currentThread().isInterrupted() || !queueToFlush.isEmpty()) {
            String name = Utils.getUniqueFileName(filesList.get());
            Path file = config.basePath().resolve(name + DATA_EXT);
            Path index = config.basePath().resolve(name + INDEX_EXT);

            ConcurrentNavigableMap<String, Entry<String>> dataToFlush = null;
            try {
                dataToFlush = queueToFlush.take();
                queueToFlush.addFirst(dataToFlush);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            try (RandomAccessFile output = new RandomAccessFile(file.toString(), "rw");
                 RandomAccessFile indexOut = new RandomAccessFile(index.toString(), "rw");
                 RandomAccessFile allFilesOut = new RandomAccessFile(catalog, "rw")
            ) {
                output.seek(0);
                output.writeInt(dataToFlush.size());
                for (Entry<String> value : dataToFlush.values()) {
                    indexOut.writeLong(output.getFilePointer());
                    Utils.writeEntry(output, value);
                }

                String firstKey = dataToFlush.firstKey();
                String lastKey = dataToFlush.lastKey();
                Deque<FileInfo> fileInfos = new ArrayDeque<>(filesList.get());
                filesLock.lock();
                try {
                    FileInfo fileInfo = new FileInfo(name, firstKey, lastKey);
                    fileInfos.addFirst(fileInfo);
                    filesList.set(fileInfos);
                } finally {
                    filesLock.unlock();
                    queueToFlush.removeFirst();
                }
                Utils.writeFileListToDisk(filesList, allFilesOut);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
