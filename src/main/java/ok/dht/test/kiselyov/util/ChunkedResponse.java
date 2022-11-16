package ok.dht.test.kiselyov.util;

import ok.dht.test.kiselyov.dao.BaseEntry;
import one.nio.http.Response;

import java.util.Iterator;

public class ChunkedResponse extends Response {
    private final Iterator<BaseEntry<byte[], Long>> entriesIterator;
    public ChunkedResponse(String resultCode, Iterator<BaseEntry<byte[], Long>> entriesIterator) {
        super(resultCode);
        super.addHeader("Content-Type: text/plain");
        super.addHeader("Transfer-Encoding: chunked");
        super.addHeader("Connection: keep-alive");
        this.entriesIterator = entriesIterator;
    }

    public Iterator<BaseEntry<byte[], Long>> getIterator() {
        return entriesIterator;
    }
}