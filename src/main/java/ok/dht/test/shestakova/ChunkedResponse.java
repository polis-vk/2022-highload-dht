package ok.dht.test.shestakova;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.shestakova.dao.base.BaseEntry;
import one.nio.http.Response;

import java.util.Iterator;

public class ChunkedResponse extends Response {
    private final Iterator<BaseEntry<MemorySegment>> entryIterator;

    public ChunkedResponse(String resultCode, Iterator<BaseEntry<MemorySegment>> entryIterator) {
        super(resultCode);
        this.entryIterator = entryIterator;
    }

    public Iterator<BaseEntry<MemorySegment>> getEntryIterator() {
        return entryIterator;
    }
}
