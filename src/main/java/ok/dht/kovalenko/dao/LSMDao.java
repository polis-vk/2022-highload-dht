package ok.dht.kovalenko.dao;

import ok.dht.ServiceConfig;
import ok.dht.kovalenko.dao.aliases.DiskSSTable;
import ok.dht.kovalenko.dao.aliases.MappedFileDiskSSTableStorage;
import ok.dht.kovalenko.dao.aliases.MemorySSTable;
import ok.dht.kovalenko.dao.aliases.MemorySSTableStorage;
import ok.dht.kovalenko.dao.aliases.TypedEntry;
import ok.dht.kovalenko.dao.base.Dao;
import ok.dht.kovalenko.dao.iterators.MergeIterator;
import ok.dht.kovalenko.dao.runnables.CompactRunnable;
import ok.dht.kovalenko.dao.runnables.FlushRunnable;
import ok.dht.kovalenko.dao.utils.DaoUtils;
import ok.dht.kovalenko.dao.visitors.ConfigVisitor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class LSMDao implements Dao<ByteBuffer, TypedEntry> {

    private static final int N_MEMORY_SSTABLES = 2;
    private static final int FLUSH_TRESHOLD_BYTES = 70 * (1 << 20); // 70MB
    private final ServiceConfig config;
    private final Serializer serializer;
    private final AtomicLong filesCounter = new AtomicLong();

    private final MemoryStorage memoryStorage = new MemoryStorage(N_MEMORY_SSTABLES);
    private final MappedFileDiskSSTableStorage diskStorage;
    private final AtomicBoolean wasCompacted = new AtomicBoolean(true);
    private final ExecutorService service = Executors.newCachedThreadPool();
    private final Runnable flushRunnable;
    private final Runnable compactRunnable;

    private final AtomicLong curBytesForEntries = new AtomicLong();

    public LSMDao(ServiceConfig config) throws IOException {
        try {
            this.config = config;
            this.serializer = new Serializer(this.wasCompacted);
            this.diskStorage = new MappedFileDiskSSTableStorage(config, this.serializer, this.filesCounter);
            if (Files.exists(config.workingDir())) {
                Files.walkFileTree(
                        config.workingDir(),
                        new ConfigVisitor(this.config, this.serializer, this.filesCounter)
                );
            } else {
                Files.createDirectory(config.workingDir());
            }

            this.flushRunnable
                    = new FlushRunnable(this.config, this.serializer, this.memoryStorage, this.filesCounter);
            this.compactRunnable
                    = new CompactRunnable(this.config, this.serializer, this.diskStorage, this.wasCompacted,
                    this.filesCounter);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public TypedEntry get(ByteBuffer key) throws IOException {
        try {
            TypedEntry res = this.memoryStorage.get(key);
            if (res == null) {
                res = this.diskStorage.get(key);
            }
            return res == null || res.isTombstone() ? null : res;
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public Iterator<TypedEntry> get(ByteBuffer from, ByteBuffer to) throws IOException {
        try {
            return new MergeIterator(this.memoryStorage.get(from, to), this.diskStorage.get(from, to));
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void upsert(TypedEntry entry) {
        try {
            if (this.curBytesForEntries.get() >= FLUSH_TRESHOLD_BYTES) {
                this.flush();
            }

            if (this.memoryStorage.writeSSTables().isEmpty()) {
                throw new RuntimeException("Very large number of upserting");
            }

            this.memoryStorage.writeSSTables().peek().put(entry.key(), entry);
            this.curBytesForEntries.addAndGet(DiskSSTable.sizeOf(entry));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void flush() throws IOException {
        if (this.memoryStorage.writeSSTables().isEmpty() || this.memoryStorage.writeSSTables().peek().isEmpty()) {
            return;
        }
        this.service.submit(this.flushRunnable);
        this.curBytesForEntries.set(0);
    }

    @Override
    public synchronized void close() throws IOException {
        try {
            this.flush();
            service.shutdown();
            if (!service.awaitTermination(5, TimeUnit.MINUTES)) {
                throw new RuntimeException("Very large number of tasks, impossible to close Dao");
            }

            if (!this.memoryStorage.empty()) {
                throw new IllegalStateException("Resources weren't released");
            }

            this.memoryStorage.clear();
            this.diskStorage.close();
            this.diskStorage.clear();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public synchronized void compact() {
        if (this.wasCompacted.get()) {
            return;
        }
        this.service.submit(this.compactRunnable);
    }

    public static final class MemoryStorage {

        private final MemorySSTableStorage memoryWriteSSTables;
        private final MemorySSTableStorage memoryFlushSSTables;

        public MemoryStorage(int size) {
            this.memoryWriteSSTables = new MemorySSTableStorage(size);
            this.memoryFlushSSTables = new MemorySSTableStorage(size);
            for (int i = 0; i < size; ++i) {
                this.memoryWriteSSTables.add(new MemorySSTable());
            }
        }

        public MemorySSTableStorage writeSSTables() {
            return this.memoryWriteSSTables;
        }

        public MemorySSTableStorage flushSSTables() {
            return this.memoryFlushSSTables;
        }

        public TypedEntry get(ByteBuffer key) {
            TypedEntry res = this.memoryWriteSSTables.get(key);
            if (res == null) {
                res = this.memoryFlushSSTables.get(key);
            }
            return res;
        }

        public List<Iterator<TypedEntry>> get(ByteBuffer from, ByteBuffer to) {
            List<Iterator<TypedEntry>> res = new ArrayList<>();
            ByteBuffer from1 = from == null ? DaoUtils.EMPTY_BYTEBUFFER : from;
            addMemorySSTables(this.memoryWriteSSTables.iterator(), res, from1, to);
            addMemorySSTables(this.memoryFlushSSTables.iterator(), res, from1, to);
            return res;
        }

        public int size() {
            return writeSSTables().size() + flushSSTables().size();
        }

        public boolean empty() {
            return !this.memoryWriteSSTables.isEmpty() && this.memoryWriteSSTables.peek().isEmpty()
                    && this.memoryFlushSSTables.isEmpty();
        }

        public void clear() {
            this.memoryWriteSSTables.clear();
            this.memoryFlushSSTables.clear();
        }

        private void addMemorySSTables(Iterator<MemorySSTable> it,
                                       List<Iterator<TypedEntry>> iterators,
                                       ByteBuffer from, ByteBuffer to) {
            while (it.hasNext()) {
                MemorySSTable memorySSTable = it.next();
                if (memorySSTable.isEmpty()) {
                    continue;
                }
                Iterator<TypedEntry> rangeIt = memorySSTable.get(from, to);
                if (rangeIt == null) {
                    continue;
                }
                iterators.add(rangeIt);
            }
        }
    }
}
