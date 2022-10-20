package ok.dht.kovalenko.dao.aliases;

import ok.dht.kovalenko.dao.utils.FileUtils;

import java.util.Map;

public abstract class DiskSSTable<PairedFileT>
        implements Map.Entry<Long/*priority*/, PairedFileT> {

    protected final long key;
    protected PairedFileT value;

    public DiskSSTable(long key, PairedFileT value) {
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
    public PairedFileT getValue() {
        return this.value;
    }

    @Override
    public PairedFileT setValue(PairedFileT value) {
        this.value = value;
        return this.value;
    }
}
