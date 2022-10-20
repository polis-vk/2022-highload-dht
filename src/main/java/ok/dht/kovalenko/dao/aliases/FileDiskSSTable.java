package ok.dht.kovalenko.dao.aliases;

import ok.dht.kovalenko.dao.dto.PairedFiles;

public class FileDiskSSTable
        extends DiskSSTable<PairedFiles> {

    public FileDiskSSTable(int key, PairedFiles value) {
        super(key, value);
    }
}
