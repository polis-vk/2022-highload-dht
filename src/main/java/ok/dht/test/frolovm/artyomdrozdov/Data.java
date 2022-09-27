package ok.dht.test.frolovm.artyomdrozdov;

import jdk.incubator.foreign.MemorySegment;
import java.io.IOException;
import java.util.Iterator;

public interface Data {
    Iterator<Entry<MemorySegment>> iterator() throws IOException;
}
