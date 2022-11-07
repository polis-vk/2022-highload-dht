package ok.dht.test.trofimov.dao.impl;

import ok.dht.test.trofimov.dao.Config;
import ok.dht.test.trofimov.dao.Dao;
import ok.dht.test.trofimov.dao.Entry;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class InMemoryDao implements Dao<String, Entry<String>> {
    protected static final String DATA_EXT = ".dat";
    protected static final String INDEX_EXT = ".ind";
    protected static final String ALL_FILES = "files.fl";
    public static final int CAPACITY = 10;

    private ConcurrentNavigableMap<String, Entry<String>> data = new ConcurrentSkipListMap<>();
    private volatile boolean commit = true;
    private final Config config;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final BlockingDeque<ConcurrentNavigableMap<String, Entry<String>>> queueToFlush =
            new LinkedBlockingDeque<>(CAPACITY);

    private final AtomicReference<Deque<FileInfo>> filesList = new AtomicReference<>(new ArrayDeque<>());
    private final AtomicLong dataSizeBytes = new AtomicLong(0);
    private final Lock filesLock = new ReentrantLock();
    private final ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService compactExecutor = Executors.newSingleThreadExecutor();

    public InMemoryDao(Config config) throws IOException {
        if (config == null) {
            throw new IllegalArgumentException("Config shouldn't be null");
        }
        this.config = config;
        loadFilesList();
        initFlush();
    }

    private void initFlush() {
        Flusher flusher = new Flusher(config, queueToFlush, filesList, filesLock);
        flushExecutor.execute(flusher);
    }

    @Override
    public Iterator<Entry<String>> get(String from, String to) throws IOException {
        String start = from;
        if (start == null) {
            start = "";
        }
        ArrayList<PeekingIterator> iterators = new ArrayList<>(filesList.get().size() + 1);
        int priority = 0;
        iterators.add(getMemoryPeekingIterator(data, start, to, priority));
        ++priority;
        var storageInQueue = queueToFlush.descendingIterator();
        while (storageInQueue.hasNext()) {
            iterators.add(getMemoryPeekingIterator(storageInQueue.next(), start, to, priority));
            priority++;
        }
        iterators.addAll(getFilePeekingIteratorList(start, to, priority).second);
        return new MergeIterator(iterators);
    }

    public Pair<Deque<FileInfo>, List<PeekingIterator>> getFilePeekingIteratorList(String from, String to,
                                                                                int initPriority) throws IOException {
        String start = from;
        if (start == null) {
            start = "";
        }
        List<PeekingIterator> fileIterators = new ArrayList<>();
        int priority = initPriority;
        Deque<FileInfo> fileListCopy = cloneFileList();
        for (FileInfo file : fileListCopy) {
            fileIterators.add(new PeekingIterator(new FileIterator(config.basePath(), file, start, to), priority));
            ++priority;
        }
        return new Pair<>(fileListCopy, fileIterators);
    }

    private Deque<FileInfo> cloneFileList() {
        Deque<FileInfo> fileListCopy;
        fileListCopy = new ArrayDeque<>(filesList.get());
        return fileListCopy;
    }

    private PeekingIterator getMemoryPeekingIterator(ConcurrentNavigableMap<String, Entry<String>> storage,
                                                     String from, String to, int priority) {
        Iterator<Entry<String>> dataIterator;
        if (to == null) {
            dataIterator = storage.tailMap(from).values().iterator();
        } else {
            dataIterator = storage.subMap(from, to).values().iterator();
        }
        return new PeekingIterator(dataIterator, priority);
    }

    @Override
    public Entry<String> get(String key) throws IOException {
        Entry<String> entry = data.get(key);
        if (entry != null) {
            return getRealEntry(entry);
        }
        for (ConcurrentNavigableMap<String, Entry<String>> storage : queueToFlush) {
            entry = storage.get(key);
            if (entry != null) {
                return getRealEntry(entry);
            }
        }
        Deque<FileInfo> filesListCopy = filesList.get();
        Path basePath = config.basePath();
        for (FileInfo file : filesListCopy) {
            entry = findInFile(key, basePath, file);
            if (entry != null && entry.key().equals(key)) {
                break;
            } else {
                entry = null;
            }
        }
        return getRealEntry(entry);
    }

    private Entry<String> findInFile(String key, Path basePath, FileInfo file) throws IOException {
        if (!isInRange(key, file)) return null;
        Entry<String> entry;
        try (RandomAccessFile raf =
                     new RandomAccessFile(basePath.resolve(file.filename() + DATA_EXT).toString(), "r")) {
            entry = Utils.findCeilEntry(raf, key, basePath.resolve(file.filename() + INDEX_EXT));
        }
        return entry;
    }

    private static boolean isInRange(String key, FileInfo file) {
        int lowerKeyComparing = key.compareTo(file.keyFirst());
        if (lowerKeyComparing < 0) {
            return false;
        }
        int highestKeyComparing = key.compareTo(file.keyLast());
        return highestKeyComparing <= 0;
    }

    private Entry<String> getRealEntry(Entry<String> entry) {
        if (entry != null && entry.value() == null) {
            return null;
        }
        return entry;
    }

    @Override
    public void upsert(Entry<String> entry) {
        lock.readLock().lock();
        try {
            Entry<String> prevVal = data.put(entry.key(), entry);
            updateSize(entry, prevVal);
            commit = false;
        } finally {
            lock.readLock().unlock();
        }
        try {
            long size = dataSizeBytes.get();
            if (size > config.flushThresholdBytes() && dataSizeBytes.compareAndSet(size, 0)) {
                flush();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void updateSize(Entry<String> entry, Entry<String> prevVal) {
        if (prevVal == null) {
            dataSizeBytes.addAndGet(2L * entry.key().getBytes(StandardCharsets.UTF_8).length
                    + (entry.isTombstone() ? 0 : entry.value().getBytes(StandardCharsets.UTF_8).length));
        } else {
            dataSizeBytes.addAndGet((entry.isTombstone() ? 0 :
                    entry.value().getBytes(StandardCharsets.UTF_8).length)
                    - (prevVal.isTombstone() ? 0 : prevVal.value().getBytes(StandardCharsets.UTF_8).length));
        }
    }

    private void loadFilesList() throws IOException {
        try (RandomAccessFile reader =
                     new RandomAccessFile(config.basePath().resolve(ALL_FILES).toString(), "r")) {
            while (reader.getFilePointer() < reader.length()) {
                String file = reader.readUTF();
                filesList.get().addFirst(Utils.getFileInfo(config.basePath(), file));
            }
        } catch (FileNotFoundException ignored) {
            //it is ok because there can be no files
        }
    }

    @Override
    public void close() throws IOException {
        flush();
        flushExecutor.shutdownNow();
        compactExecutor.shutdown();
        try {
            flushExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
            compactExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void flush() throws IOException {
        if (commit) {
            return;
        }
        if (queueToFlush.size() == CAPACITY) {
            throw new IllegalStateException("Exceeded limit");
        }
        queueToFlush.add(data);
        lock.writeLock().lock();
        try {
            data = new ConcurrentSkipListMap<>();
            commit = true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void compact() throws IOException {
        if (filesList.get().size() <= 1) {
            return;
        }
        compactExecutor.execute(new Compacter(config, filesList, this, filesLock));
    }
}
