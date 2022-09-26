package ok.dht.test.kurdyukov.db.dao.storage;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.kurdyukov.db.base.Entry;

import java.io.IOException;
import java.util.Iterator;

public interface Data {
        Iterator<Entry<MemorySegment>> iterator() throws IOException;
    }