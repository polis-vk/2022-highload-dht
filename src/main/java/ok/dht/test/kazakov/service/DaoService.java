package ok.dht.test.kazakov.service;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.kazakov.dao.BaseEntry;
import ok.dht.test.kazakov.dao.Dao;
import ok.dht.test.kazakov.dao.Entry;
import one.nio.util.Utf8;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;

public final class DaoService implements Closeable {

    private final Dao<MemorySegment, Entry<MemorySegment>> dao;

    public DaoService(final Dao<MemorySegment, Entry<MemorySegment>> dao) {
        this.dao = dao;
    }

    private MemorySegment toMemorySegment(final String value) {
        return toMemorySegment(Utf8.toBytes(value));
    }

    private MemorySegment toMemorySegment(final byte[] value) {
        return MemorySegment.ofArray(Arrays.copyOf(value, value.length));
    }

    public byte[] get(final String id) throws IOException {
        final Entry<MemorySegment> resultEntry = dao.get(toMemorySegment(id));
        return resultEntry == null ? null : resultEntry.value().toByteArray();
    }

    public void delete(final String id) {
        dao.upsert(new BaseEntry<>(toMemorySegment(id), null));
    }

    public void upsert(final String id, final byte[] value) {
        dao.upsert(new BaseEntry<>(toMemorySegment(id), toMemorySegment(value)));
    }

    @Override
    public void close() throws IOException {
        dao.close();
    }
}
