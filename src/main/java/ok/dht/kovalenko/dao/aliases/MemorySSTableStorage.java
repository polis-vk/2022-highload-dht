package ok.dht.kovalenko.dao.aliases;

import java.util.concurrent.LinkedBlockingDeque;

public final class MemorySSTableStorage
        extends LinkedBlockingDeque<MemorySSTable> {
    public MemorySSTableStorage(int capacity) {
        super(capacity);
    }
}
