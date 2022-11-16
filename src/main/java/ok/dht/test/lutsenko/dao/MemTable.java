package ok.dht.test.lutsenko.dao;

import ok.dht.test.lutsenko.dao.common.BaseEntry;

import java.util.Iterator;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class MemTable {

    private final ConcurrentSkipListMap<String, BaseEntry<String>> data = new ConcurrentSkipListMap<>();
    private final AtomicLong bytes = new AtomicLong();
    private final AtomicBoolean onFlush = new AtomicBoolean(false);

    public Iterator<BaseEntry<String>> iterator(String from, String to) {
        if (from == null && to == null) {
            return data.values().iterator();
        }
        if (from == null) {
            return data.headMap(to).values().iterator();
        }
        if (to == null) {
            return data.tailMap(from).values().iterator();
        }
        return data.subMap(from, to).values().iterator();
    }

    public void put(BaseEntry<String> entry) {
        data.put(entry.key(), entry);
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    public AtomicLong getBytes() {
        return bytes;
    }

    public AtomicBoolean onFlush() {
        return onFlush;
    }

}
