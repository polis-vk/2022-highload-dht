package ok.dht.test.labazov.dao;

public class MemorySegmentDaoState {
    final Config config;
    final MemorySegmentDaoMemory memory;
    final MemorySegmentDaoMemory flushing;
    final Storage storage;
    final boolean closed;

    MemorySegmentDaoState(Config config, MemorySegmentDaoMemory memory,
                          MemorySegmentDaoMemory flushing, Storage storage) {
        this.config = config;
        this.memory = memory;
        this.flushing = flushing;
        this.storage = storage;
        this.closed = false;
    }

    MemorySegmentDaoState(Config config, Storage storage, boolean closed) {
        this.config = config;
        this.memory = MemorySegmentDaoMemory.EMPTY;
        this.flushing = MemorySegmentDaoMemory.EMPTY;
        this.storage = storage;
        this.closed = closed;
    }

    static MemorySegmentDaoState newState(Config config, Storage storage) {
        return new MemorySegmentDaoState(
                config,
                new MemorySegmentDaoMemory(config.flushThresholdBytes()),
                MemorySegmentDaoMemory.EMPTY,
                storage
        );
    }

    public MemorySegmentDaoState prepareForFlush() {
        checkNotClosed();
        if (isFlushing()) {
            throw new IllegalStateException("Already flushing");
        }
        return new MemorySegmentDaoState(
                config,
                new MemorySegmentDaoMemory(config.flushThresholdBytes()),
                memory,
                storage
        );
    }

    public MemorySegmentDaoState afterFlush(Storage storage) {
        checkNotClosed();
        if (!isFlushing()) {
            throw new IllegalStateException("Wasn't flushing");
        }
        return new MemorySegmentDaoState(
                config,
                memory,
                MemorySegmentDaoMemory.EMPTY,
                storage
        );
    }

    public MemorySegmentDaoState afterCompact(Storage storage) {
        checkNotClosed();
        return new MemorySegmentDaoState(
                config,
                memory,
                flushing,
                storage
        );
    }

    public MemorySegmentDaoState afterClosed() {
        checkNotClosed();
        if (!storage.isClosed()) {
            throw new IllegalStateException("Storage should be closed early");
        }
        return new MemorySegmentDaoState(config, storage, true);
    }

    public void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("Already closed");
        }
    }

    public boolean isFlushing() {
        return this.flushing != MemorySegmentDaoMemory.EMPTY;
    }
}
