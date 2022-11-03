package ok.dht.test.maximenko.dao;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MemorySegmentDao implements Dao<MemorySegment, Entry<MemorySegment>> {
    private static final Comparator<MemorySegment> COMPARATOR = NaturalOrderComparator.getInstance();
    private static final MemorySegment VERY_FIRST_KEY = MemorySegment.ofArray(new byte[]{});
    private static final long NULL_VALUE_SIZE = -1;
    private static final int FLUSHING_QUEUE_SIZE = 1;
    private static final long DEFAULT_TABLE_SPACE = 1024 * 1024 * 128;
    private static int SUCCESS = 1;
    private static int FLUSH_REQUEST = -1;
    private static int TABLE_READ_ONLY = -2;
    volatile boolean flushFinish;
    private MemTable activeMemTable;
    BlockingQueue<MemTable> flushingTables = new ArrayBlockingQueue<>(FLUSHING_QUEUE_SIZE);
    List<SSTable> ssTables;
    private ExecutorService flusher;
    private ExecutorService compactor;
    final ReadWriteLock dbTablesLock = new ReentrantReadWriteLock();
    private final Config config;
    final ResourceScope scope = ResourceScope.globalScope();
    final FileManager fileManager;
    volatile MemTable flushingTable;

    public MemorySegmentDao() throws IOException {
        this(null);
    }

    public MemorySegmentDao(Config config) throws IOException {
        this.config = config;
        this.compactor = Executors.newSingleThreadExecutor();

        fileManager = new FileManager(config);

        if (Files.exists(fileManager.getCompactTmpFile())) {
            finishCompacting();
        }

        flushFinish = false;
        flusher = Executors.newSingleThreadExecutor();
        activeMemTable = createMemoryTable();
        if (config == null) {
            ssTables = null;
        } else {
            List<Path> logPaths = fileManager.getLogPaths();
            ssTables = new ArrayList<>(logPaths.size());

            for (Path logPath : logPaths) {
                ssTables.add(0, new SSTable(logPath, scope));
            }
        }
    }

    @Override
    public Iterator<Entry<MemorySegment>> get(MemorySegment from, MemorySegment to) throws IOException {

        MemorySegment fromValue = from;

        if (from == null) {
            fromValue = VERY_FIRST_KEY;
        }

        if (to == null) {
            return new BorderedMergeIterator(fromValue, null, getAvailableTables());
        }
        return new BorderedMergeIterator(fromValue, to, getAvailableTables());

    }

    // should get them in correct order. Less is more recent
    private List<Table> getAvailableTables() {
        List<Table> result = new LinkedList<>();
        dbTablesLock.readLock().lock();
        try {
            result.add(activeMemTable);
            result.addAll(flushingTables);
            if (flushingTable != null) {
                result.add(flushingTable);
            }
            result.addAll(ssTables);
        } finally {
            dbTablesLock.readLock().unlock();
        }

        return result;
    }

    @Override
    public Entry<MemorySegment> get(MemorySegment key) throws IOException {
        Iterator<Entry<MemorySegment>> iterator = get(key, null);
        if (!iterator.hasNext()) {
            return null;
        }
        Entry<MemorySegment> next = iterator.next();
        if (COMPARATOR.compare(next.key(), key) == 0) {
            return next;
        }
        return null;
    }

    @Override
    public void upsert(Entry<MemorySegment> entry) {
        while (true) {
            int insertResult = activeMemTable.put(entry.key(), entry);
            if (insertResult == SUCCESS) {
                break;
            }
            if (insertResult == TABLE_READ_ONLY) {
                // #fix Me polling
                continue;
            }

            assert insertResult == FLUSH_REQUEST;
            dbTablesLock.writeLock().lock();
            try {
                if (flushingTables.size() < FLUSHING_QUEUE_SIZE) {
                    flushWithoutLocking();
                } else {
                    throw new OutOfMemoryError();
                }
            } finally {
                dbTablesLock.writeLock().unlock();
            }
        }
    }

    public void flushWithoutLocking() {
        flushingTables.add(activeMemTable);
        activeMemTable = createMemoryTable();
        flusher.submit(new Flusher(this));
    }

    @Override
    public void flush() {
        dbTablesLock.writeLock().lock();
        try {
            if (flushingTables.size() < FLUSHING_QUEUE_SIZE) {
                flushWithoutLocking();
            } else {
                throw new OutOfMemoryError();
            }
        } finally {
            dbTablesLock.writeLock().unlock();
        }
    }

    private void finishCompacting() throws IOException {
        fileManager.addLog(fileManager.getCompactTmpFile());
    }

    // values_amount index1 index2 ... indexN k1_size v1_size k1 v1 ....
    @Override
    public void close() throws IOException {
        if (!scope.isAlive()) {
            return;
        }
        flush();
        flusher.shutdown();
        compactor.shutdown();
        while (true) {
            try {
                if (flusher.awaitTermination(24L, TimeUnit.HOURS)) {
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        while (true) {
            try {
                if (compactor.awaitTermination(24L, TimeUnit.HOURS)) {
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private MemTable createMemoryTable() {
        if (config == null) {
            return new MemTable(DEFAULT_TABLE_SPACE);
        }

        return new MemTable(config.flushThresholdBytes());
    }

    public void writeValuesToFile(Iterable<Entry<MemorySegment>> values,
                                  Path fileName)
            throws IOException {
        try (ResourceScope writeScope = ResourceScope.newConfinedScope()) {
            long size = Long.BYTES;
            long valuesAmount = 0;

            for (Entry<MemorySegment> value : values) {
                valuesAmount++;
                if (value.value() == null) {
                    size += value.key().byteSize();
                } else {
                    size += value.value().byteSize() + value.key().byteSize();
                }

                // index, key size, value size
                size += 3L * Long.BYTES;
            }

            if (Files.exists(fileName)) {
                Files.delete(fileName);
            }
            Files.createFile(fileName);
            MemorySegment log =
                    MemorySegment.mapFile(
                            fileName,
                            0,
                            size,
                            FileChannel.MapMode.READ_WRITE,
                            writeScope);

            MemoryAccess.setLongAtOffset(log, 0, valuesAmount);
            long indexOffset = Long.BYTES;
            long dataOffset = Long.BYTES + valuesAmount * Long.BYTES;

            for (Entry<MemorySegment> value : values) {
                MemoryAccess.setLongAtOffset(log, indexOffset, dataOffset);
                indexOffset += Long.BYTES;

                MemoryAccess.setLongAtOffset(log, dataOffset, value.key().byteSize());
                dataOffset += Long.BYTES;
                if (value.value() == null) {
                    MemoryAccess.setLongAtOffset(log, dataOffset, NULL_VALUE_SIZE);
                } else {
                    MemoryAccess.setLongAtOffset(log, dataOffset, value.value().byteSize());
                }

                dataOffset += Long.BYTES;

                log.asSlice(dataOffset).copyFrom(value.key());
                dataOffset += value.key().byteSize();
                if (value.value() != null) {
                    log.asSlice(dataOffset).copyFrom(value.value());
                    dataOffset += value.value().byteSize();
                }
            }
        }
    }

    @Override
    public void compact() throws IOException {
        compactor.execute(new Compactor(this));
    }
}
