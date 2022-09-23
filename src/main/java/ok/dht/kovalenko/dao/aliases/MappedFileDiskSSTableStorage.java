package ok.dht.kovalenko.dao.aliases;


import ok.dht.ServiceConfig;
import ok.dht.kovalenko.dao.Serializer;
import ok.dht.kovalenko.dao.dto.FileMeta;
import ok.dht.kovalenko.dao.dto.MappedPairedFiles;
import ok.dht.kovalenko.dao.utils.FileUtils;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class MappedFileDiskSSTableStorage
        extends DiskSSTableStorage<MappedFileDiskSSTable>
        implements Closeable {

    private static final Method unmap;
    private static final Object unsafe;

    static {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            unmap = unsafeClass.getMethod("invokeCleaner", ByteBuffer.class);
            unmap.setAccessible(true);
            Field theUnsafeField = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafeField.setAccessible(true);
            unsafe = theUnsafeField.get(null); // 'sun.misc.Unsafe' instance
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private final Thread mapThread;
    private final ServiceConfig config;
    private final Serializer serializer;

    public MappedFileDiskSSTableStorage(ServiceConfig config, Serializer serializer) {
        this.config = config;
        this.serializer = serializer;
        this.mapThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    try (Stream<Path> paths = Files.list(config.workingDir())) {
                        long pathsCount = paths.count();
                        serializer.get(pathsCount / 2);
                    } catch (NullPointerException ignored) {
                    }
                }
            } catch (ClosedByInterruptException ignored) {
            } catch (IOException | ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
        this.mapThread.setDaemon(true);
        this.mapThread.start();
    }

    @Override
    public void close() throws IOException {
        try {
            this.mapThread.interrupt();
            if (!this.mapThread.isInterrupted()) {
                throw new IllegalStateException("Can't end a work");
            }
            unmapMappedFiles();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean isCompacted() {
        return size() == 1 && !serializer.tombstoned(this.firstEntry().getValue().getValue());
    }

    private void unmapMappedFiles() throws ReflectiveOperationException {
        for (MappedFileDiskSSTable mappedFileDiskSSTable : this.values()) {
            unmap(mappedFileDiskSSTable.getValue());
        }
    }

    public void mapForRead(long nTablesToMap) throws IOException, ReflectiveOperationException {
        unmapMappedFiles();

        for (long priority = 1; priority <= nTablesToMap; ++priority) {
            Path dataFile = FileUtils.getFilePath(FileUtils.getDataFilename(priority), this.config);
            Path indexesFile = FileUtils.getFilePath(FileUtils.getIndexesFilename(priority), this.config);

            try (FileChannel dataChannel = FileChannel.open(dataFile);
                 FileChannel indexesChannel = FileChannel.open(indexesFile)) {
                MappedByteBuffer mappedDataFile =
                        dataChannel.map(FileChannel.MapMode.READ_ONLY, 0, dataChannel.size());
                MappedByteBuffer mappedIndexesFile =
                        indexesChannel.map(FileChannel.MapMode.READ_ONLY, 0, indexesChannel.size());
                mappedDataFile.position(FileMeta.size());
                this.put(
                        priority,
                        new MappedFileDiskSSTable(priority, new MappedPairedFiles(mappedDataFile, mappedIndexesFile))
                );
            }
        }
    }

    private void unmap(MappedPairedFiles mappedPairedFiles) throws ReflectiveOperationException {
        unmap(mappedPairedFiles.dataFile());
        unmap(mappedPairedFiles.indexesFile());
    }

    private void unmap(MappedByteBuffer buffer) throws ReflectiveOperationException {
        unmap.invoke(unsafe, buffer);
    }
}
