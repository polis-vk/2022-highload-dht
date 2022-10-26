package ok.dht.test.kovalenko.dao.aliases;

import ok.dht.test.kovalenko.dao.dto.PairedFiles;

public class FileDiskSSTable
        extends DiskSSTable<PairedFiles> {

    public FileDiskSSTable(int key, PairedFiles value) {
        super(key, value);
    }
}
