package ok.dht.kovalenko.dao.aliases;


import ok.dht.kovalenko.dao.dto.MappedPairedFiles;

public class MappedFileDiskSSTable
        extends DiskSSTable<MappedPairedFiles> {

    public MappedFileDiskSSTable(long key, MappedPairedFiles value) {
        super(key, value);
    }
}
