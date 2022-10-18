package ok.dht.test.saskov.database.drozdov;

import ok.dht.test.saskov.database.Config;

public class State {
    final Config config;
    final Memory memory;
    final Memory flushing;
    final Storage storage;
    final boolean closed;

    State(Config config, Memory memory, Memory flushing, Storage storage) {
        this.config = config;
        this.memory = memory;
        this.flushing = flushing;
        this.storage = storage;
        this.closed = false;
    }

    State(Config config, Storage storage, boolean closed) {
        this.config = config;
        this.memory = Memory.EMPTY;
        this.flushing = Memory.EMPTY;
        this.storage = storage;
        this.closed = closed;
    }

    static State newState(Config config, Storage storage) {
        return new State(
                config,
                new Memory(config.flushThresholdBytes()),
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
                config,
                new Memory(config.flushThresholdBytes()),
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
                config,
                memory,
                Memory.EMPTY,
                storage
        );
    }

    public State afterCompact(Storage storage) {
        checkNotClosed();
        return new State(
                config,
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
        return new State(config, storage, true);
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
