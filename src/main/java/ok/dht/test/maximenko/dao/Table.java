package ok.dht.test.maximenko.dao;


import jdk.incubator.foreign.MemorySegment;

import java.io.IOException;
import java.util.Iterator;

public interface Table extends Iterable<Entry<MemorySegment>> {
    Iterator<Entry<MemorySegment>> get() throws IOException;

    Iterator<Entry<MemorySegment>> get(MemorySegment from, MemorySegment to) throws IOException;
}
