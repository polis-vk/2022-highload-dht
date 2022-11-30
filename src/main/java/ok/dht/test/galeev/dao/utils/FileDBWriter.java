package ok.dht.test.galeev.dao.utils;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import ok.dht.test.galeev.dao.entry.Entry;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

public class FileDBWriter implements Closeable {
    public static final String FILE_TMP = "file.tmp";
    private static final boolean OFF_HASH = true;
    public static final byte VALUE_FOR_HASH_NULL = (byte) -1;
    private final Path path;
    private final ResourceScope writeScope;

    public FileDBWriter(Path path) {
        this.writeScope = ResourceScope.newConfinedScope();
        this.path = path;
    }

    // first value is number of entries, second is byte size
    private static IteratorData getIteratorData(
            Iterator<Entry<MemorySegment, MemorySegment>> iterator,
            boolean offHash) {
        MessageDigest md = null;
        if (!offHash) {
            try {
                md = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("System does not found SHA-256 algorithm", e);
            }
        }
        long numberOfElements = 0;
        long byteSize = 0;
        while (iterator.hasNext()) {
            Entry<MemorySegment, MemorySegment> entry = iterator.next();
            byteSize += getEntryLength(entry);
            if (!offHash) {
                updateHash(md, entry);
            }
            numberOfElements++;
        }
        byteSize += Long.BYTES + numberOfElements * Long.BYTES;
        if (offHash) {
            return new IteratorData(numberOfElements, byteSize, new byte[0]);
        } else {
            return new IteratorData(numberOfElements, byteSize, md.digest());
        }
    }

    static void updateHash(MessageDigest md, Entry<MemorySegment, MemorySegment> entry) {
        md.update(entry.key().asReadOnly().asByteBuffer());
        if (entry.value() == null) {
            md.update(VALUE_FOR_HASH_NULL);
        } else {
            md.update(entry.value().asReadOnly().asByteBuffer());
        }
    }

    public static long getEntryLength(Entry<MemorySegment, MemorySegment> entry) {
        return entry.key().byteSize()
                + ((entry.value() == null) ? 0 : entry.value().byteSize()) + 2 * Long.BYTES;
    }

    private static long writeEntry(MemorySegment page, long posToWrite, Entry<MemorySegment, MemorySegment> baseEntry) {
        long offset = 0;

        MemoryAccess.setLongAtOffset(page, posToWrite + offset, baseEntry.key().byteSize());
        offset += Long.BYTES;
        MemoryAccess.setLongAtOffset(page, posToWrite + offset,
                baseEntry.value() == null ? -1 : baseEntry.value().byteSize());
        offset += Long.BYTES;

        page.asSlice(posToWrite + offset, baseEntry.key().byteSize()).copyFrom(baseEntry.key());
        offset += baseEntry.key().byteSize();
        if (baseEntry.value() != null) {
            page.asSlice(posToWrite + offset, baseEntry.value().byteSize()).copyFrom(baseEntry.value());
            offset += baseEntry.value().byteSize();
        }
        return offset;
    }

    private static MemorySegment createTmpMemorySegmentPage(long mapByteSize,
                                                            Path tmpPath,
                                                            ResourceScope writeScope) throws IOException {
        Files.deleteIfExists(tmpPath);
        Files.createFile(tmpPath);
        return MemorySegment.mapFile(
                tmpPath,
                0,
                mapByteSize,
                FileChannel.MapMode.READ_WRITE,
                writeScope
        );
    }

    private static void writeIterable(
            MemorySegment page,
            long numberOfEntries,
            Iterator<Entry<MemorySegment, MemorySegment>> iterator,
            byte[] sha256) {
        MemoryAccess.setLongAtOffset(page, 0, numberOfEntries);

        // offset for data
        long dataBeingOffset = Long.BYTES + (long) Long.BYTES * numberOfEntries;

        //offset for hash
        dataBeingOffset += Long.BYTES + sha256.length;

        long i = 0;
        long dataWriteOffset = 0;
        while (iterator.hasNext()) {
            Entry<MemorySegment, MemorySegment> entry = iterator.next();
            MemoryAccess.setLongAtOffset(page, Long.BYTES + Long.BYTES * i++, dataWriteOffset);

            dataWriteOffset += writeEntry(page, dataBeingOffset + dataWriteOffset, entry);
        }

        MemoryAccess.setLongAtOffset(page, Long.BYTES + Long.BYTES * i++, sha256.length);
        page.asSlice(Long.BYTES + Long.BYTES * i, sha256.length).copyFrom(MemorySegment.ofArray(sha256));
    }

    /*
     * Param iterableCollection which we are going to write
     * Returns if had something been written
     */
    public boolean writeIterable(
            Iterable<Entry<MemorySegment, MemorySegment>> iterableCollection
    ) throws IOException {
        Iterator<Entry<MemorySegment, MemorySegment>> iterator = iterableCollection.iterator();

        if (!iterator.hasNext()) {
            return false;
        }

        IteratorData iteratorData = getIteratorData(iterator, OFF_HASH);
        iterator = iterableCollection.iterator();
        writeIteratorWithTempFile(iterator, iteratorData);
        return true;
    }

    private void writeIteratorWithTempFile(Iterator<Entry<MemorySegment, MemorySegment>> iterator,
                                           IteratorData iteratorData) throws IOException {
        byte[] sha256 = iteratorData.sha256();
        String fileName = path.getFileName().toString();
        long fileId = Long.parseLong(fileName.substring(0, fileName.length() - 4));
        Path tmpPath = path.getParent().resolve("tmp" + fileId + ".txt");

        MemorySegment page = createTmpMemorySegmentPage(
                iteratorData.dataArraySize() + sha256.length + Long.BYTES,
                tmpPath, writeScope);

        writeIterable(page, iteratorData.numberOfEntries(), iterator, sha256);

        FileDBReader reader = new FileDBReader(page);
        if (!OFF_HASH && reader.checkIfFileCorrupted()) {
            throw new FileSystemException("File with path: " + path + " has written incorrectly");
        }

        Files.move(tmpPath, path, StandardCopyOption.ATOMIC_MOVE);
    }

    @Override
    public void close() throws IOException {
        writeScope.close();
    }

    @SuppressWarnings({"unused", "all"})
    private record IteratorData(long numberOfEntries, long dataArraySize, byte[] sha256) {
    }
}
