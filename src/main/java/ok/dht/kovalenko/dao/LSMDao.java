package ok.dht.kovalenko.dao;

import ok.dht.Dao;
import ok.dht.ServiceConfig;
import ok.dht.kovalenko.dao.aliases.DiskSSTable;
import ok.dht.kovalenko.dao.aliases.MemorySSTable;
import ok.dht.kovalenko.dao.aliases.MemorySSTableStorage;
import ok.dht.kovalenko.dao.aliases.TypedEntry;
import ok.dht.kovalenko.dao.iterators.MergeIterator;
import ok.dht.kovalenko.dao.runnables.CompactRunnable;
import ok.dht.kovalenko.dao.runnables.FlushRunnable;
import ok.dht.kovalenko.dao.utils.DaoUtils;
import ok.dht.kovalenko.dao.utils.FileUtils;
import ok.dht.kovalenko.dao.visitors.ConfigVisitor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LSMDao implements Dao<ByteBuffer, TypedEntry> {

    private static final int N_MEMORY_SSTABLES = 2;
    private static final int FLUSH_TRESHOLD_BYTES = 1 << 20; // 1MB
    private final ServiceConfig config;
    private final Serializer serializer;
    private final ReadWriteLock rwlock = new ReentrantReadWriteLock();

    private final MemorySSTableStorage memoryWriteSSTables = new MemorySSTableStorage(N_MEMORY_SSTABLES);
    private final MemorySSTableStorage memoryFlushSSTables = new MemorySSTableStorage(N_MEMORY_SSTABLES);
    private final AtomicBoolean wasCompacted = new AtomicBoolean(true);
    private final ExecutorService service = Executors.newCachedThreadPool();
    private final Runnable flushRunnable;
    private final Runnable compactRunnable;

    private AtomicLong curBytesForEntries = new AtomicLong();

    public LSMDao(ServiceConfig config) throws IOException {
        try {
            this.config = config;
            this.serializer = new Serializer(this.config);
            Files.walkFileTree(
                    config.workingDir(),
                    new ConfigVisitor(this.config, this.serializer)
            );

            for (int i = 0; i < N_MEMORY_SSTABLES; ++i) {
                this.memoryWriteSSTables.add(new MemorySSTable());
            }

            this.flushRunnable
                    = new FlushRunnable(this.config, this.serializer, this.memoryWriteSSTables, this.memoryFlushSSTables);
            this.compactRunnable = new CompactRunnable(this.config, this.serializer);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public Iterator<TypedEntry> get(ByteBuffer from, ByteBuffer to) throws IOException {
        this.rwlock.readLock().lock();
        try {
            MemorySSTableStorage memorySSTables
                    = new MemorySSTableStorage(this.memoryWriteSSTables.size() + this.memoryFlushSSTables.size());
            addMemorySSTablesIfNotEmpty(this.memoryWriteSSTables.iterator(), memorySSTables);
            // The newest SSTables to be flushed is the most priority for us
            addMemorySSTablesIfNotEmpty(this.memoryFlushSSTables.iterator(), memorySSTables);
            return new MergeIterator(from, to, this.serializer, this.config, memorySSTables);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        } finally {
            this.rwlock.readLock().unlock();
        }
    }

    @Override
    public void upsert(TypedEntry entry) {
        this.rwlock.writeLock().lock();
        try {
            if (this.curBytesForEntries.get() >= FLUSH_TRESHOLD_BYTES) {
                this.flushRunnable.run();
                //this.service.submit(this.flushRunnable);
                this.wasCompacted.set(false);
                this.curBytesForEntries.set(0);
            }

            if (this.memoryWriteSSTables.isEmpty()) {
                throw new RuntimeException("Very large number of upserting");
            }

            this.memoryWriteSSTables.peek().put(entry.key(), entry);
            this.curBytesForEntries.addAndGet(DiskSSTable.sizeOf(entry));
        } finally {
            this.rwlock.writeLock().unlock();
        }
    }

    @Override
    public void flush() throws IOException {
        if (DaoUtils.isEmpty(this.memoryWriteSSTables)) {
            return;
        }
        this.flushRunnable.run();
        //this.service.submit(this.flushRunnable);
        this.wasCompacted.set(false);
    }

    @Override
    public synchronized void close() throws IOException {
        try {
            flush();
            service.shutdown();
            if (!service.awaitTermination(5, TimeUnit.MINUTES)) {
                throw new RuntimeException("Very large number of tasks, impossible to close Dao");
            }

            if (!this.memoryFlushSSTables.isEmpty()
                    || this.memoryWriteSSTables.isEmpty()
                    || !this.memoryWriteSSTables.peek().isEmpty()) {
                throw new IllegalStateException("Resources weren't released");
            }

            this.memoryWriteSSTables.clear();
            this.serializer.close();
            this.curBytesForEntries = null;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void compact() throws IOException {
        if (this.wasCompacted.get()) {
            return;
        }
        try {
            long nFiles = FileUtils.nFiles(this.config);
            if (nFiles == 0
                    || (nFiles == 2 && serializer.meta(this.serializer.get(1).dataFile()).notTombstoned())) {
                return;
            }
            this.service.submit(this.compactRunnable);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void addMemorySSTablesIfNotEmpty(Iterator<MemorySSTable> memorySSTables,
                                             MemorySSTableStorage memorySSTableStorage) {
        while (memorySSTables.hasNext()) {
            MemorySSTable memorySSTable = memorySSTables.next();
            if (!memorySSTable.isEmpty()) {
                memorySSTableStorage.add(memorySSTable);
            }
        }
    }
}
