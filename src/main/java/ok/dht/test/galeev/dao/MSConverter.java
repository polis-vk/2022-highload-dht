package ok.dht.test.galeev.dao;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.galeev.dao.entry.BaseEntry;
import ok.dht.test.galeev.dao.entry.Entry;

public interface MSConverter<K, V> {
    K MStoK(MemorySegment ms);
    V MStoV(MemorySegment ms);

    MemorySegment KtoMS(K key);
    MemorySegment VtoMS(V val);

    default Entry<K,V> MSEtoEntry(Entry<MemorySegment, MemorySegment> entry) {
        if (entry == null) {
            return null;
        }
        return new BaseEntry<>(
                MStoK(entry.key()),
                MStoV(entry.value())
        );
    }
    default Entry<MemorySegment, MemorySegment> EntryToMSE(Entry<K,V> entry) {
        return new BaseEntry<>(
                KtoMS(entry.key()),
                VtoMS(entry.value())
        );
    }
}
