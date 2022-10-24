package ok.dht.test.lutsenko.dao;

import java.nio.MappedByteBuffer;

public class FileInfo {
    public final int fileNumber;
    public final MappedByteBuffer mappedByteBuffer;
    public final ThreadLocal<Integer> position;

    public FileInfo(int fileNumber, MappedByteBuffer mappedByteBuffer, ThreadLocal<Integer> position) {
        this.fileNumber = fileNumber;
        this.mappedByteBuffer = mappedByteBuffer;
        this.position = position;
    }
}
