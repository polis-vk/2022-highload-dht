package ok.dht.kovalenko.dao.aliases;

import java.util.concurrent.ConcurrentSkipListMap;

public abstract class DiskSSTableStorage<FileType extends DiskSSTable<?>>
        extends ConcurrentSkipListMap<Long, FileType> {
    protected DiskSSTableStorage() {
    }
}
