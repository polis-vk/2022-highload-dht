package ok.dht.test.kiselyov.dao.impl;

import ok.dht.test.kiselyov.dao.BaseEntry;

public class EntryWithTimestamp {
    private final BaseEntry<byte[]> entry;
    private final Long timestamp;

    public EntryWithTimestamp(BaseEntry<byte[]> entry, Long timestamp) {
        this.entry = entry;
        this.timestamp = timestamp;
    }

    public BaseEntry<byte[]> getEntry() {
        return entry;
    }

    public Long getTimestamp() {
        return timestamp;
    }
}
