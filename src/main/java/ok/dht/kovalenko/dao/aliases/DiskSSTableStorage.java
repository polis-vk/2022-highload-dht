package ok.dht.kovalenko.dao.aliases;

import java.util.concurrent.ConcurrentSkipListMap;

public abstract class DiskSSTableStorage<FileT extends DiskSSTable<?>>
        extends ConcurrentSkipListMap<Long, FileT> {
    protected DiskSSTableStorage() {
        // it is ok
    }
}
