package ok.dht.kovalenko.dao.aliases;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingDeque;

public final class MemorySSTableStorage
        extends LinkedBlockingDeque<MemorySSTable> {

    public static MemorySSTableStorage EMPTY = new MemorySSTableStorage(1);

    public MemorySSTableStorage(int capacity) {
        super(capacity);
    }

    public TypedEntry get(ByteBuffer key) {
        TypedEntry res = null;
        for (Iterator<MemorySSTable> it = this.descendingIterator(); it.hasNext(); ) {
            MemorySSTable memorySSTable = it.next();
            if ((res = memorySSTable.get(key)) != null) {
                break;
            }
        }
        return res;
    }

}
