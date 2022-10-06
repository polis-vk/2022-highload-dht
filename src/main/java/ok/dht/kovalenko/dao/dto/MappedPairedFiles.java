package ok.dht.kovalenko.dao.dto;

import ok.dht.kovalenko.dao.Serializer;
import ok.dht.kovalenko.dao.utils.FileUtils;

import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;

public class MappedPairedFiles {

    private final MappedByteBuffer dataFile;
    private final MappedByteBuffer indexesFile;
    private final ByteBufferRange range;

    public MappedPairedFiles(MappedByteBuffer dataFile, MappedByteBuffer indexesFile, Serializer serializer) {
        this.dataFile = dataFile;
        this.indexesFile = indexesFile;
        ByteBuffer fromRange = serializer.readKey(this, 0);
        ByteBuffer toRange = serializer.readKey(this, indexesFile.limit() - FileUtils.INDEX_SIZE);
        this.range = new ByteBufferRange(fromRange, toRange);
    }

    public MappedByteBuffer dataFile() {
        return dataFile;
    }

    public MappedByteBuffer indexesFile() {
        return indexesFile;
    }

    public ByteBufferRange range() {
        return this.range;
    }

    public int dataLimit() {
        return dataFile.limit();
    }

    public int indexesLimit() {
        return indexesFile.limit();
    }

}
