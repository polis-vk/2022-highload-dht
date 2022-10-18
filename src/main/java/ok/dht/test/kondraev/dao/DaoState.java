package ok.dht.test.kondraev.dao;

/*
 * Provides atomic access to MemoryTable + Storage state via
 * `final` guarantees + volatile variable State.
 */
final class DaoState {
    final Storage storage;
    final MemoryTable memoryTable;
    final MemoryTable flushingTable;
    final boolean isClosed;

    private DaoState(Storage storage, MemoryTable memoryTable, MemoryTable flushingTable, boolean isClosed) {
        this.storage = storage;
        this.memoryTable = memoryTable;
        this.flushingTable = flushingTable;
        this.isClosed = isClosed;
    }

    boolean isFlushing() {
        return flushingTable != null;
    }

    DaoState afterFlush(Storage storage) {
        assertNotClosed();
        return new DaoState(storage, memoryTable, null, false);
    }

    void assertNotClosed() {
        if (isClosed) {
            throw new IllegalStateException("Dao is already closed");
        }
    }

    DaoState flushing() {
        assertNotClosed();
        if (isFlushing()) {
            throw new IllegalStateException("Trying to flush twice: already flushing");
        }
        return new DaoState(storage, new MemoryTable(), memoryTable, false);
    }

    DaoState afterClosed() {
        assertNotClosed();
        if (!storage.isClosed()) {
            throw new IllegalStateException("Storage should be closed before Dao");
        }
        return new DaoState(storage, memoryTable, flushingTable, true);
    }

    static DaoState newState(Storage storage) {
        return new DaoState(storage, new MemoryTable(), null, false);
    }

    DaoState afterCompact(Storage storage) {
        assertNotClosed();
        return new DaoState(storage, memoryTable, flushingTable, false);
    }
}
