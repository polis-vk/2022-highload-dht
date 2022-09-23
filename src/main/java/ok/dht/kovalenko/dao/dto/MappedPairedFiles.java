package ok.dht.kovalenko.dao.dto;

import java.nio.MappedByteBuffer;

public record MappedPairedFiles(MappedByteBuffer dataFile, MappedByteBuffer indexesFile) {
    public long dataLimit() {
        return dataFile.limit();
    }

    public long indexesLimit() {
        return indexesFile.limit();
    }
}
