package ok.dht.test.kuleshov;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.kuleshov.dao.Entry;
import one.nio.util.ByteArrayBuilder;
import one.nio.util.Utf8;

import java.util.Iterator;

public class ChunkIterator implements Iterator<byte[]> {
    private static final byte[] CHUNK_SEPARATOR = Utf8.toBytes("\r\n");
    private static final byte[] KEY_VALUE_SEPARATOR = Utf8.toBytes("\n");
    private static final byte[] END = Utf8.toBytes("0\r\n\r\n");
    Iterator<Entry<MemorySegment>> entryIterator;
    boolean isEnd = false;

    public ChunkIterator(Iterator<Entry<MemorySegment>> entryIterator) {
        this.entryIterator = entryIterator;
    }

    @Override
    public boolean hasNext() {
        return !isEnd;
    }

    @Override
    public byte[] next() {
        if (isEnd) {
            return null;
        }

        if (!entryIterator.hasNext()) {
            isEnd = true;

            return END;
        }

        Entry<MemorySegment> entry = entryIterator.next();
        ByteArrayBuilder builder = new ByteArrayBuilder();
        long size = entry.key().byteSize() + 1 + entry.value().byteSize();

        builder.append(Utf8.toBytes(Long.toHexString(size)))
                .append(CHUNK_SEPARATOR)
                .append(entry.key().toByteArray())
                .append(KEY_VALUE_SEPARATOR)
                .append(entry.value().toByteArray())
                .append(CHUNK_SEPARATOR);

        return builder.toBytes();
    }
}
