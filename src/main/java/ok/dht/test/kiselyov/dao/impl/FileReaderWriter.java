package ok.dht.test.kiselyov.dao.impl;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

public class FileReaderWriter implements Closeable {
    private final RandomAccessFile rafFile;
    private final FileChannel channelFile;
    private final RandomAccessFile rafIndex;
    private final FileChannel channelIndex;

    public FileReaderWriter(Path filePath, Path indexPath) throws IOException {
        rafFile = new RandomAccessFile(String.valueOf(filePath), "rw");
        channelFile = rafFile.getChannel();
        rafIndex = new RandomAccessFile(String.valueOf(indexPath), "rw");
        channelIndex = rafIndex.getChannel();
    }

    public FileChannel getFileChannel() {
        return channelFile;
    }

    public FileChannel getIndexChannel() {
        return channelIndex;
    }

    @Override
    public void close() throws IOException {
        channelFile.close();
        channelIndex.close();
        rafFile.close();
        rafIndex.close();
    }
}
