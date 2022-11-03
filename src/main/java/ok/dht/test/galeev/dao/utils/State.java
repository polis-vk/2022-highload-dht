package ok.dht.test.galeev.dao.utils;

import java.io.IOException;

public class State {
    public final boolean isFlushing;
    public final Memory memory;
    public final Memory flushing;
    public final DBReader storage;

    public State(boolean isFlushing, Memory memory, Memory flushing, DBReader storage) {
        this.isFlushing = isFlushing;
        this.memory = memory;
        this.flushing = flushing;
        this.storage = storage;
    }

    public State prepareForFlush() throws IOException {
        if (flushing != null) {
            throw new IOException();
        }
        return new State(true, new Memory(), memory, storage);
    }

    public State afterFlush(DBReader storage) throws IOException {
        if (flushing == null) {
            throw new IOException();
        }
        return new State(false, memory, null, storage);
    }

    public State afterCompact(DBReader storage) {
        return new State(isFlushing, memory, flushing, storage);
    }
}
