package ok.dht.test.galeev.dao;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.galeev.dao.entry.BaseEntry;
import ok.dht.test.galeev.dao.entry.Entry;
import ok.dht.test.galeev.dao.utils.DaoConfig;

import java.io.IOException;

public class DaoMiddleLayer<K, V> {
    private final MemorySegmentDao dao;
    private final MSConverter<K, V> converter;

    public DaoMiddleLayer(DaoConfig daoConfig, MSConverter<K, V> converter) throws IOException {
        dao = new MemorySegmentDao(daoConfig);
        this.converter = converter;
    }

    public Entry<K, V> get(K k) {
        Entry<MemorySegment, MemorySegment> entry = dao.get(converter.getMSFromKey(k));
        if (entry == null) {
            return null;
        }
        return new BaseEntry<>(
                k,
                converter.getValFromMS(entry.value())
        );
    }

    public void upsert(Entry<K, V> entry) {
        dao.upsert(converter.entryToEntryMS(entry));
    }

    public void upsert(K k, V v) {
        dao.upsert(new BaseEntry<>(
                converter.getMSFromKey(k),
                converter.getMSFromVal(v)
        ));
    }

    public void delete(K k) {
        dao.upsert(new BaseEntry<>(converter.getMSFromKey(k), null));
    }

    public void stop() throws IOException {
        dao.close();
    }
}
