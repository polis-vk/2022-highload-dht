package ok.dht.kovalenko.dao.aliases;

import ok.dht.ServiceConfig;
import ok.dht.kovalenko.dao.Serializer;
import ok.dht.kovalenko.dao.dto.FileMeta;
import ok.dht.kovalenko.dao.dto.MappedPairedFiles;
import ok.dht.kovalenko.dao.utils.DaoUtils;
import ok.dht.kovalenko.dao.utils.FileUtils;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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

    private final Serializer serializer;
    private final ServiceConfig config;
    private final AtomicLong filesCounter;

    public MappedFileDiskSSTableStorage(ServiceConfig config, Serializer serializer, AtomicLong filesCounter) {
        super();
        this.config = config;
        this.serializer = serializer;
        this.filesCounter = filesCounter;
    }

    @Override
    public void close() throws IOException {
        try {
            unmapFiles();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void unmapFiles() throws ReflectiveOperationException {
        for (MappedFileDiskSSTable mappedFileDiskSSTable : this.values()) {
            unmap(mappedFileDiskSSTable.getValue());
        }
    }

    private void mapForRead(long numTablesToMap, Serializer serializer)
            throws IOException, ReflectiveOperationException {
        unmapFiles();

        for (long priority = 1; priority <= numTablesToMap; ++priority) {
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
                        new MappedFileDiskSSTable(priority,
                                new MappedPairedFiles(mappedDataFile, mappedIndexesFile, serializer), serializer)
                );
            }
        }
    }

    public TypedEntry get(ByteBuffer key) throws IOException, ReflectiveOperationException {
        updateTables();
        TypedEntry res = null;
        for (MappedFileDiskSSTable diskSSTable : this.descendingMap().values()) {
            if ((res = diskSSTable.get(key)) != null) {
                return res;
            }
        }
        return res;
    }

    public List<Iterator<TypedEntry>> get(ByteBuffer from, ByteBuffer to)
            throws ReflectiveOperationException, IOException {
        updateTables();
        List<Iterator<TypedEntry>> res = new ArrayList<>();
        ByteBuffer from1 = from == null ? DaoUtils.EMPTY_BYTEBUFFER : from;
        for (MappedFileDiskSSTable diskSSTable : this.descendingMap().values()) {
            Iterator<TypedEntry> rangeIt = diskSSTable.get(from1, to);
            if (rangeIt == null) {
                continue;
            }
            res.add(rangeIt);
        }
        return res;
    }

    private void unmap(MappedPairedFiles mappedPairedFiles) throws ReflectiveOperationException {
        unmap(mappedPairedFiles.dataFile());
        unmap(mappedPairedFiles.indexesFile());
    }

    private void unmap(MappedByteBuffer buffer) throws ReflectiveOperationException {
        unmap.invoke(unsafe, buffer);
    }

    private void updateTables() throws ReflectiveOperationException, IOException {
        long numFiles = filesCounter.get() / 2;
        if (this.get(numFiles) == null) {
            mapForRead(numFiles, serializer);
        }
    }
}
