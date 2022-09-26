package ok.dht.test.galeev.dao;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.galeev.dao.entry.BaseEntry;
import ok.dht.test.galeev.dao.entry.Entry;

public interface MSConverter<K, V> {
    K getKeyFromMS(MemorySegment ms);
    V getValFromMS(MemorySegment ms);

    MemorySegment getMSFromKey(K key);
    MemorySegment getMSFromVal(V val);

    default Entry<K,V> EntryMStoEntry(Entry<MemorySegment, MemorySegment> entry) {
        if (entry == null) {
            return null;
        }
        return new BaseEntry<>(
                getKeyFromMS(entry.key()),
                getValFromMS(entry.value())
        );
    }

    default Entry<MemorySegment, MemorySegment> EntryToEntryMS(Entry<K,V> entry) {
        return new BaseEntry<>(
                getMSFromKey(entry.key()),
                getMSFromVal(entry.value())
        );
    }
}
