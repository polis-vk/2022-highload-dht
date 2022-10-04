package ok.dht.test.gerasimov;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.gerasimov.lsm.BaseEntry;
import ok.dht.test.gerasimov.lsm.Dao;
import ok.dht.test.gerasimov.lsm.Entry;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;

/**
 * @author Michael Gerasimov
 */
public final class DaoService implements Closeable {

    private final Dao<MemorySegment, Entry<MemorySegment>> dao;

    public DaoService(final Dao<MemorySegment, Entry<MemorySegment>> dao) {
        this.dao = dao;
    }

    public Optional<byte[]> get(final String id) throws IOException {
        MemorySegment memorySegmentId = MemorySegment.ofArray(id.getBytes());
        Entry<MemorySegment> entry = dao.get(memorySegmentId);

        if (entry == null) {
            return Optional.empty();
        }

        return Optional.of(entry.value().toByteArray());
    }

    public void upsert(final String id, final byte[] data) {
        MemorySegment memorySegmentId = MemorySegment.ofArray(id.getBytes());
        MemorySegment memorySegmentValue = MemorySegment.ofArray(data);
        Entry<MemorySegment> entry = new BaseEntry<>(memorySegmentId, memorySegmentValue);
        dao.upsert(entry);
    }

    public void delete(final String id) {
        MemorySegment memorySegmentId = MemorySegment.ofArray(id.getBytes());
        Entry<MemorySegment> entry = new BaseEntry<>(memorySegmentId, null);
        dao.upsert(entry);
    }

    @Override
    public void close() throws IOException {
        dao.close();
    }
}
