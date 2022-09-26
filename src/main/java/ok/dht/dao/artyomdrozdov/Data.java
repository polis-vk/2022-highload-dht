package ok.dht.dao.artyomdrozdov;

import java.io.IOException;
import java.util.Iterator;
import jdk.incubator.foreign.MemorySegment;

public interface Data {
    Iterator<Entry<MemorySegment>> iterator() throws IOException;
}