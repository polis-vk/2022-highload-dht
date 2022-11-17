package ok.dht.test.shakhov.http.stream;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.shakhov.dao.Entry;
import one.nio.http.Response;

import java.util.Iterator;

public class StreamResponse extends Response {
    private static final String TRANSFER_ENCODING_CHUNKED_HEADER = "Transfer-Encoding: chunked";

    private final Iterator<Entry<MemorySegment>> streamIterator;

    public StreamResponse(Iterator<Entry<MemorySegment>> streamIterator) {
        super(Response.OK);
        addHeader(TRANSFER_ENCODING_CHUNKED_HEADER);
        this.streamIterator = streamIterator;
    }

    public Iterator<Entry<MemorySegment>> getStreamIterator() {
        return streamIterator;
    }
}
