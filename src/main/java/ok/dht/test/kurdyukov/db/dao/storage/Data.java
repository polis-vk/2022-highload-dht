package ok.dht.test.kurdyukov.db.dao.storage;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.kurdyukov.db.base.Entry;

public interface Data extends Iterable<Entry<MemorySegment>> {

}
