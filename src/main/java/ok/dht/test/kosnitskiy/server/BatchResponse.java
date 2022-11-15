package ok.dht.test.kosnitskiy.server;

import java.util.Iterator;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.kosnitskiy.dao.Entry;
import one.nio.http.Response;

public class BatchResponse extends Response {
    private final Iterator<Entry<MemorySegment>> iterator;

    public BatchResponse(String resultCode, Iterator<Entry<MemorySegment>> iterator) {
        super(resultCode);
        super.addHeader("Transfer-Encoding: chunked");
        this.iterator = iterator;
    }

    public Iterator<Entry<MemorySegment>> getIterator() {
        return iterator;
    }
}
