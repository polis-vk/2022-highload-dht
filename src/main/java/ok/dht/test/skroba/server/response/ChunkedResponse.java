package ok.dht.test.skroba.server.response;

import one.nio.http.Response;
import org.iq80.leveldb.DBIterator;

public class ChunkedResponse extends Response {
    private static final String TRANSFER_ENCODING = "Transfer-Encoding: chunked";
    private static final String CLOSE_CONNECTION = "Connection: close";
    
    final DBIterator iterator;
    final byte[] end;
    
    @SuppressWarnings("PMD.ArrayIsStoredDirectly")
    public ChunkedResponse(final String resultCode, final DBIterator iterator, final byte[] end) {
        super(resultCode);
        this.iterator = iterator;
        this.end = end;
        addHeader(TRANSFER_ENCODING);
        addHeader(CLOSE_CONNECTION);
    }
}
