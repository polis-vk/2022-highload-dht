package ok.dht.test.shakhov.dao;

public class State {
    final DaoConfig daoConfig;
    final Memory memory;
    final Memory flushing;
    final Storage storage;
    final boolean closed;

    public State(DaoConfig daoConfig, Memory memory, Memory flushing, Storage storage) {
        this.daoConfig = daoConfig;
        this.memory = memory;
        this.flushing = flushing;
        this.storage = storage;
        this.closed = false;
    }

    public State(DaoConfig daoConfig, Storage storage, boolean closed) {
        this.daoConfig = daoConfig;
        this.memory = Memory.EMPTY;
        this.flushing = Memory.EMPTY;
        this.storage = storage;
        this.closed = closed;
    }

    public static State newState(DaoConfig daoConfig, Storage storage) {
        return new State(
                daoConfig,
                new Memory(daoConfig.flushThresholdBytes()),
                Memory.EMPTY,
                storage
        );
    }

    public State prepareForFlush() {
        checkNotClosed();
        if (isFlushing()) {
            throw new IllegalStateException("Already flushing");
        }
        return new State(
                daoConfig,
                new Memory(daoConfig.flushThresholdBytes()),
                memory,
                storage
        );
    }

    public State afterFlush(Storage storage) {
        checkNotClosed();
        if (!isFlushing()) {
            throw new IllegalStateException("Wasn't flushing");
        }
        return new State(
                daoConfig,
                memory,
                Memory.EMPTY,
                storage
        );
    }

    public State afterCompact(Storage storage) {
        checkNotClosed();
        return new State(
                daoConfig,
                memory,
                flushing,
                storage
        );
    }

    public State afterClosed() {
        checkNotClosed();
        if (!storage.isClosed()) {
            throw new IllegalStateException("Storage should be closed early");
        }
        return new State(daoConfig, storage, true);
    }

    public void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("Already closed");
        }
    }

    public boolean isFlushing() {
        return this.flushing != Memory.EMPTY;
    }
}
