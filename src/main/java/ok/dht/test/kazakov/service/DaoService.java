package ok.dht.test.kazakov.service;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.kazakov.dao.Dao;
import ok.dht.test.kazakov.dao.Entry;
import ok.dht.test.kazakov.dao.MemorySegmentEntry;
import one.nio.util.Utf8;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.StringJoiner;

public final class DaoService implements Closeable {

    private static final int IS_TOMBSTONE_OR_ABSENT_FLAG_BIT = 0;
    private static final int IS_ABSENT_FLAG_BIT = 1;
    private static final int FLAGS_START = 0;
    private static final int ADDITIONAL_VALUE_START = FLAGS_START + Byte.BYTES;
    private static final int TIMESTAMP_START = ADDITIONAL_VALUE_START + Long.BYTES;
    private static final int DATA_START = TIMESTAMP_START + Long.BYTES;

    private final Dao<MemorySegment, Entry<MemorySegment>> dao;

    public DaoService(@Nonnull final Dao<MemorySegment, Entry<MemorySegment>> dao) {
        this.dao = dao;
    }

    private static MemorySegment toMemorySegment(@Nonnull final String value) {
        return toMemorySegment(Utf8.toBytes(value));
    }

    private static MemorySegment toMemorySegment(@Nonnull final byte[] value) {
        return MemorySegment.ofArray(value);
    }

    public static long extractLongFromBytes(@Nonnull final byte[] bytes, final int from) {
        long result = 0;
        for (int i = 0; i < Long.BYTES; i++) {
            result |= ((long) bytes[from + i]) << (i * Byte.SIZE);
        }
        return result;
    }

    public static void putLongIntoBytes(@Nonnull final byte[] bytes, final int from, final long value) {
        final long byteMask = (1 << Byte.SIZE) - 1;
        for (int i = 0; i < Long.BYTES; i++) {
            bytes[from + i] = (byte) ((value >> (i * Byte.SIZE)) & byteMask);
        }
    }

    public static LazyByteArrayEntry fromRawBytes(@Nonnull final byte[] data) {
        return new LazyByteArrayEntry(data);
    }

    public static byte[] toRawBytes(@Nullable final Entry<MemorySegment> entry,
                                    final long additionalValue,
                                    final long defaultTimestamp) {
        final int valueSize = entry == null || entry.isTombstone() ? 0 : (int) entry.getValue().byteSize();
        final byte[] result = new byte[Long.BYTES + Long.BYTES + Byte.BYTES + valueSize];

        result[FLAGS_START] |= (byte) (entry == null || entry.isTombstone() ? 1 << IS_TOMBSTONE_OR_ABSENT_FLAG_BIT : 0);
        result[FLAGS_START] |= (byte) (entry == null ? 1 << IS_ABSENT_FLAG_BIT : 0);
        putLongIntoBytes(result, ADDITIONAL_VALUE_START, additionalValue);
        putLongIntoBytes(result, TIMESTAMP_START, entry == null ? defaultTimestamp : entry.getTimestamp());

        for (int i = 0; i < valueSize; i++) {
            result[DATA_START + i] = MemoryAccess.getByteAtOffset(entry.getValue(), i);
        }

        return result;
    }

    @Nonnull
    public Iterator<Entry<MemorySegment>> get(@Nonnull final String from,
                                              @Nullable final String to) throws IOException {
        return dao.get(toMemorySegment(from), to == null ? null : toMemorySegment(to));
    }

    @Nullable
    public Entry<MemorySegment> get(@Nonnull final String id) throws IOException {
        return dao.get(toMemorySegment(id));
    }

    public void delete(final String id, final long timestamp) {
        dao.upsert(new MemorySegmentEntry(toMemorySegment(id), null, timestamp));
    }

    public void upsert(final String id, final byte[] value, final long timestamp) {
        dao.upsert(new MemorySegmentEntry(toMemorySegment(id), toMemorySegment(value), timestamp));
    }

    @Override
    public void close() throws IOException {
        dao.close();
    }

    public static class LazyByteArrayEntry implements Entry<byte[]> {
        private final byte[] rawData;

        // Suppressing code-climate check to avoid redundant array copying
        @SuppressWarnings("PMD.ArrayIsStoredDirectly")
        private LazyByteArrayEntry(final byte[] rawData) {
            this.rawData = rawData;
        }

        @Override
        public byte[] getKey() {
            throw new UnsupportedOperationException("LazyByteArrayEntry contains no key");
        }

        @Override
        public byte[] getValue() {
            return isTombstone() ? null : Arrays.copyOfRange(rawData, DATA_START, rawData.length);
        }

        @Override
        public long getTimestamp() {
            return extractLongFromBytes(rawData, TIMESTAMP_START);
        }

        @Override
        public byte[] getValueBytes() {
            return getValue();
        }

        private boolean getFlag(final int bit) {
            return ((rawData[FLAGS_START] >> bit) & 1) == 1;
        }

        public long getAdditionalValue() {
            return extractLongFromBytes(rawData, ADDITIONAL_VALUE_START);
        }

        @Override
        public boolean isTombstone() {
            return getFlag(IS_TOMBSTONE_OR_ABSENT_FLAG_BIT);
        }

        public boolean isAbsent() {
            return getFlag(IS_ABSENT_FLAG_BIT);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", "LazyByteArrayEntry[", "]")
                    .add("getValue().length=" + Objects.requireNonNullElse(getValue(), new byte[0]).length)
                    .add("timestamp=" + getTimestamp())
                    .add("isTombstone=" + isTombstone())
                    .add("isAbsent=" + isAbsent())
                    .toString();
        }
    }
}
