package ok.dht.test.lutsenko.dao;

import ok.dht.test.lutsenko.dao.common.BaseEntry;
import ok.dht.test.lutsenko.dao.common.Dao;
import ok.dht.test.lutsenko.dao.common.DaoConfig;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

import static ok.dht.test.lutsenko.dao.DaoUtils.mapFile;
import static ok.dht.test.lutsenko.dao.DaoUtils.unmap;

/**
 * ----------------------------------------------------------------------------------------------*
 * Описание формата файла.
 * - Минимальный ключ во всем файле
 * - Максимальный ключ во всем файле
 * - 0 - Длина предыдущей entry для первой entry
 * - В цикле для всех entry:
 * - Длина ключа
 * - Ключ
 * - EXISTING_MARK или DELETED_MARK
 * - Значение, если не равно null
 * -'\n'
 * - Длина всего записанного + размер самого числа относительно типа char
 * Пример (пробелы и переносы строк для наглядности):
 * k2 k55
 * 0 2 k2 1 v2 '\n'
 * 10 3 k40 1 v40 '\n'
 * 12 3 k55 1 v5555 '\n'
 * 14 5 ka123 0 '\n'
 * 11
 * ----------------------------------------------------------------------------------------------*
 **/
public class PersistenceRangeDao implements Dao<String, BaseEntry<String>> {

    public static final String DATA_FILE_NAME = "daoData";
    public static final String MEMORY_FILE_NAME = "memory";
    public static final String COMPACTION_FILE_NAME = "compaction";
    public static final String DATA_FILE_EXTENSION = ".txt";
    public static final String TEMP_FILE_EXTENSION = ".tmp";
    private final AtomicInteger tmpCounter = new AtomicInteger(0);
    private final AtomicInteger currentFileNumber = new AtomicInteger(0);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final DaoConfig daoConfig;
    private final MemStorage memStorage;
    private final ConcurrentSkipListMap<Path, FileInfo> filesMap = new ConcurrentSkipListMap<>(
            Comparator.comparingInt(this::getFileNumber)
    );
    private final ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService compactionExecutor = Executors.newSingleThreadExecutor();

    public PersistenceRangeDao(DaoConfig daoConfig) throws IOException {
        this.daoConfig = daoConfig;
        memStorage = new MemStorage(daoConfig.flushThresholdBytes());
        try (Stream<Path> stream = Files.find(daoConfig.basePath(), 1,
                (p, a) -> a.isRegularFile() && p.getFileName().toString().endsWith(DATA_FILE_EXTENSION))) {
            List<Path> paths = stream.toList();
            for (Path path : paths) {
                ThreadLocal<Integer> position = new ThreadLocal<>();
                position.set(0);
                filesMap.put(path, new FileInfo(getFileNumber(path), mapFile(path), position));
            }
            currentFileNumber.set(filesMap.isEmpty() ? 0 : getFileNumber(filesMap.lastKey()) + 1);
        } catch (NoSuchFileException e) {
            filesMap.clear();
            currentFileNumber.set(0);
        }
    }

    @Override
    public Iterator<BaseEntry<String>> get(String from, String to) {
        checkNotClosed();
        lock.readLock().lock();
        try {
            return new MergeIterator(this, from, to, true);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void upsert(BaseEntry<String> entry) {
        checkNotClosed();
        int entryBytes = DaoUtils.bytesOf(entry);
        lock.readLock().lock();
        try {
            if (memStorage.firstTableOnFlush().get()) {
                memStorage.upsertToSecondTable(entry, entryBytes);
            } else if (memStorage.firstTableBytes().addAndGet(entryBytes) < daoConfig.flushThresholdBytes()) {
                memStorage.putFirstTable(entry);
            } else {
                if (memStorage.firstTableNotOnFlushAndSetTrue()) {
                    flushFirstMemTable();
                }
                memStorage.upsertToSecondTable(entry, entryBytes);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void flush() {
        checkNotClosed();
        lock.writeLock().lock(); // Только для ручного flush, автоматический соответствует вызову flushFirstMemTable()
        try {
            if (memStorage.firstTableNotOnFlushAndSetTrue()) {
                flushFirstMemTable();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void flushFirstMemTable() {
        flushExecutor.execute(() -> {
            try {
                Path tempMemoryFilePath = generateTempPath(MEMORY_FILE_NAME);
                DaoUtils.writeToFile(tempMemoryFilePath, firstTableIterator());
                Path dataFilePath = generateNextFilePath();
                Files.move(tempMemoryFilePath, dataFilePath);
                MappedByteBuffer mappedByteBuffer = mapFile(dataFilePath);
                lock.writeLock().lock();
                try {
                    ThreadLocal<Integer> position = new ThreadLocal<>();
                    position.set(0);
                    filesMap.put(dataFilePath, new FileInfo(getFileNumber(dataFilePath), mappedByteBuffer, position));
                    memStorage.clearFirstTable();
                } finally {
                    lock.writeLock().unlock();
                }
            } catch (IOException e) {
                throw new RuntimeException("Flush first table failed", e);
            }
        });
    }

    @Override
    public void close() throws IOException {
        if (isClosed.getAndSet(true)) {
            return;
        }
        shutdownAndAwaitTermination(flushExecutor);
        shutdownAndAwaitTermination(compactionExecutor);
        if (!memStorage.isEmpty()) {
            DaoUtils.writeToFile(generateNextFilePath(), inMemoryDataIterator(null, null));
        }
        for (FileInfo fileInfo : filesMap.values()) {
            unmap(fileInfo.mappedByteBuffer);
        }
        filesMap.clear();
    }

    @Override
    public void compact() {
        checkNotClosed();
        lock.readLock().lock();
        try {
            if (filesMap.size() < 2) {
                return;
            }
        } finally {
            lock.readLock().unlock();
        }
        // Comact-им только файлы существующие на момент вызова компакта.
        // Начинаем писать compact во временный файл,
        // после переименовываем его в обычный самый приоритетный файл
        // Состояние валидно - остальные файлы не тронуты, временный не учитывается при чтении/записи
        // Начинаем удалять старые файлы: все кроме только что созданного. После переименовываем compact-файл в первый
        // При ошибке выше чтение будет из корректного compact-файла,
        // который останется самым приоритетным файлом существующие на момент вызова компакта
        // Независимо от успеха/ошибок в действиях выше, файлы записанные после compact-a будут приоритетнее
        compactionExecutor.execute(() -> {
            Path tempCompactionFilePath = generateTempPath(COMPACTION_FILE_NAME);
            Path lastFilePath = generateNextFilePath();
            MappedByteBuffer lastFileInputStream;
            List<Map.Entry<Path, FileInfo>> compactionFileInfosMapEntries;
            try {
                MergeIterator allEntriesIterator = new MergeIterator(this, null, null, false);
                compactionFileInfosMapEntries = allEntriesIterator.getFileInfos(); // copy of filesMap in iterator
                DaoUtils.writeToFile(tempCompactionFilePath, allEntriesIterator);
                Files.move(tempCompactionFilePath, lastFilePath);
                lastFileInputStream = mapFile(lastFilePath);
            } catch (IOException e) {
                throw new RuntimeException("Writing to temp file failed", e);
            }
            lock.writeLock().lock();
            try {
                ThreadLocal<Integer> position = new ThreadLocal<>();
                position.set(0);
                filesMap.put(lastFilePath, new FileInfo(getFileNumber(lastFilePath), lastFileInputStream, position));
                for (Map.Entry<Path, FileInfo> filesMapEntry : compactionFileInfosMapEntries) {
                    filesMap.remove(filesMapEntry.getKey());
                    unmap(filesMapEntry.getValue().mappedByteBuffer);
                    Files.delete(filesMapEntry.getKey());
                }
            } catch (IOException e) {
                throw new RuntimeException("Deleting old files failed", e);
            } finally {
                lock.writeLock().unlock();
            }
        });
    }

    public Iterator<BaseEntry<String>> inMemoryDataIterator(String from, String to) {
        lock.readLock().lock();
        try {
            return memStorage.iterator(from, to);
        } finally {
            lock.readLock().unlock();
        }
    }

    private Iterator<BaseEntry<String>> firstTableIterator() {
        lock.readLock().lock();
        try {
            return memStorage.firstTableIterator(null, null);
        } finally {
            lock.readLock().unlock();
        }
    }

    private Path generateNextFilePath() {
        return daoConfig.basePath().resolve(DATA_FILE_NAME + currentFileNumber.getAndIncrement() + DATA_FILE_EXTENSION);
    }

    private Path generateTempPath(String fileName) {
        return daoConfig.basePath().resolve(fileName + tmpCounter.getAndIncrement() + TEMP_FILE_EXTENSION);
    }

    public DaoConfig getConfig() {
        return daoConfig;
    }

    public Map<Path, FileInfo> getFileInfosMap() {
        lock.readLock().lock();
        try {
            return filesMap;
        } finally {
            lock.readLock().unlock();
        }
    }

    private int getFileNumber(Path path) {
        String filename = path.getFileName().toString();
        return Integer.parseInt(filename.substring(
                DATA_FILE_NAME.length(),
                filename.length() - DATA_FILE_EXTENSION.length()));
    }

    private void shutdownAndAwaitTermination(ExecutorService executorService) {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(Integer.MAX_VALUE, TimeUnit.HOURS)) {
                executorService.shutdownNow();
                throw new RuntimeException("Await termination too long");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("AwaitTermination interrupted", e);
        }
    }

    private void checkNotClosed() {
        if (isClosed.get()) {
            throw new RuntimeException("Cannot operate on closed dao");
        }
    }
}
