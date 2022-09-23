package ok.dht.kovalenko.dao.aliases;


import ok.dht.kovalenko.dao.utils.FileUtils;

import java.util.Map;

public abstract class DiskSSTable<PairedFileType>
        implements Map.Entry<Long/*priority*/, PairedFileType> {

    protected final long key;
    protected PairedFileType value;

    public DiskSSTable(long key, PairedFileType value) {
        this.key = key;
        this.value = value;
    }

    public static int sizeOf(TypedEntry entry) {
        return MemorySSTable.sizeOf(entry) + FileUtils.INDEX_SIZE;
    }

    @Override
    public Long getKey() {
        return this.key;
    }

    @Override
    public PairedFileType getValue() {
        return this.value;
    }

    @Override
    public PairedFileType setValue(PairedFileType value) {
        return (this.value = value);
    }
}
