package ok.dht.kovalenko.dao.dto;

import java.nio.MappedByteBuffer;

public record MappedPairedFiles(MappedByteBuffer dataFile, MappedByteBuffer indexesFile) {
    public int dataLimit() {
        return dataFile.limit();
    }

    public int indexesLimit() {
        return indexesFile.limit();
    }
}
