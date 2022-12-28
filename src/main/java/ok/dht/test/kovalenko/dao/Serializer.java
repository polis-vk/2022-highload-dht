package ok.dht.test.kovalenko.dao;

import ok.dht.test.kovalenko.dao.aliases.MemorySSTable;
import ok.dht.test.kovalenko.dao.aliases.TypedBaseTimedEntry;
import ok.dht.test.kovalenko.dao.aliases.TypedTimedEntry;
import ok.dht.test.kovalenko.dao.dto.FileMeta;
import ok.dht.test.kovalenko.dao.dto.MappedPairedFiles;
import ok.dht.test.kovalenko.dao.dto.PairedFiles;
import ok.dht.test.kovalenko.dao.utils.DaoUtils;
import ok.dht.test.kovalenko.dao.utils.FileUtils;
import ok.dht.test.kovalenko.dao.utils.MergeIteratorUtils;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Serializer {

    private final AtomicBoolean wasCompacted;

    public Serializer(AtomicBoolean wasCompacted)
            throws ReflectiveOperationException {
        this.wasCompacted = wasCompacted;
    }

    public TypedTimedEntry readEntry(MappedPairedFiles mappedFilePair, int indexesPos) {
        int dataPos = readDataFileOffset(mappedFilePair.indexesFile(), indexesPos);
        final long timestamp = readTimestamp(mappedFilePair.dataFile(), dataPos);
        dataPos += Long.BYTES;
        byte tombstone = readByte(mappedFilePair.dataFile(), dataPos);
        ++dataPos;
        ByteBuffer key = readByteBuffer(mappedFilePair.dataFile(), dataPos);
        dataPos += (Integer.BYTES + key.remaining());
        ByteBuffer value =
                MergeIteratorUtils.isTombstone(tombstone)
                        ? null
                        : readByteBuffer(mappedFilePair.dataFile(), dataPos);
        return new TypedBaseTimedEntry(timestamp, key, value);
    }

    public ByteBuffer readKey(MappedPairedFiles mappedFilePair, int indexesPos) {
        int dataPos = readDataFileOffset(mappedFilePair.indexesFile(), indexesPos);
        return readByteBuffer(mappedFilePair.dataFile(), dataPos + DaoUtils.TIMESTAMP_LENGTH + 1);
    }

    public void write(Iterator<TypedTimedEntry> data, PairedFiles pairedFiles)
            throws IOException {
        if (!data.hasNext()) {
            return;
        }

        Path dataFilePath = pairedFiles.dataFile();
        Path indexesFilePath = pairedFiles.indexesFile();

        try (RandomAccessFile dataFile = new RandomAccessFile(dataFilePath.toString(), "rw");
             RandomAccessFile indexesFile = new RandomAccessFile(indexesFilePath.toString(), "rw")) {
            byte hasTombstones = FileMeta.HAS_NOT_TOMBSTONES;
            writeMeta(new FileMeta(FileMeta.INCOMPLETELY_WRITTEN, FileMeta.HAS_NOT_TOMBSTONES), dataFile);

            int curOffset = (int) dataFile.getFilePointer();
            int bbSize = 0;
            ByteBuffer offset = ByteBuffer.allocate(FileUtils.INDEX_SIZE);
            TypedTimedEntry curEntry;
            while (data.hasNext()) {
                curOffset += bbSize;
                writeOffset(curOffset, offset, indexesFile);
                curEntry = data.next();
                hasTombstones = curEntry.isTombstone() ? FileMeta.HAS_TOMBSTONES : FileMeta.HAS_NOT_TOMBSTONES;
                bbSize = writeEntry(curEntry, dataFile);
            }

            writeMeta(new FileMeta(FileMeta.COMPLETELY_WRITTEN, hasTombstones), dataFile);
            if (hasTombstones == FileMeta.HAS_TOMBSTONES) {
                this.wasCompacted.set(true);
            }
        } catch (Exception ex) {
            Files.delete(dataFilePath);
            Files.delete(indexesFilePath);
            throw new RuntimeException(ex);
        }
    }

    public FileMeta meta(Path pathToFile) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(pathToFile.toString(), "r")) {
            byte completelyWritten = file.readByte();
            byte hasTombstones = file.readByte();
            return new FileMeta(completelyWritten, hasTombstones);
        }
    }

    private void writeMeta(FileMeta meta, RandomAccessFile file) throws IOException {
        file.seek(0);
        file.write(meta.completelyWritten());
        file.write(meta.hasTombstones());
    }

    private int readDataFileOffset(MappedByteBuffer indexesFile, int indexesPos) {
        return indexesFile.getInt(indexesPos);
    }

    private long readTimestamp(MappedByteBuffer dataFile, int dataPos) {
        return dataFile.getLong(dataPos);
    }

    private byte readByte(MappedByteBuffer dataFile, int dataPos) {
        return dataFile.get(dataPos);
    }

    private ByteBuffer readByteBuffer(MappedByteBuffer dataFile, int dataPos) {
        int bbSize = dataFile.getInt(dataPos);
        return dataFile.slice(dataPos + Integer.BYTES, bbSize);
    }

    /*
     * Write offsets in format:
     * ┌─────┐
     * │ int │
     * └─────┘
     */
    private void writeOffset(int offset, ByteBuffer bbOffset, RandomAccessFile indexesFile) throws IOException {
        bbOffset.putInt(offset);
        bbOffset.rewind();
        indexesFile.getChannel().write(bbOffset);
        bbOffset.rewind();
    }

    /*
     * Write entries in format:
     * ┌───────────────────┬───────────────────┬
     * │ timestamp: byte[] │ isTombstone: byte │
     * └───────────────────┴───────────────────┴
     * ┬──────────────┬─────────────┬────────────────┬───────────────┐
     * │ keySize: int │ key: byte[] │ valueSize: int │ value: byte[] │
     * ┴──────────────┴─────────────┴────────────────┴───────────────┘
     */
    private int writeEntry(TypedTimedEntry entry, RandomAccessFile dataFile) throws IOException {
        int bbSize = MemorySSTable.sizeOf(entry);
        ByteBuffer pair = ByteBuffer.allocate(bbSize);
        byte tombstone = MergeIteratorUtils.getTombstoneValue(entry);

        pair.putLong(entry.timestamp());
        pair.put(tombstone);
        pair.putInt(entry.key().remaining());
        pair.put(entry.key());

        if (!entry.isTombstone()) {
            pair.putInt(entry.value().remaining());
            pair.put(entry.value());
        }

        pair.rewind();
        dataFile.getChannel().write(pair);

        return bbSize;
    }
}
