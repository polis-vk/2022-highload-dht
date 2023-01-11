package ok.dht.test.kurdyukov.http;

import one.nio.http.Response;
import org.iq80.leveldb.DBIterator;

class HttpChunkedResponse extends Response {
    private static final String TRANSFER_ENCODING_CHUNKED_HEADER = "Transfer-Encoding: chunked";

    public DBIterator iterator;
    public String upperBound;

    public HttpChunkedResponse(
            String resultCode,
            DBIterator iterator,
            String upperBound
    ) {
        super(resultCode);

        this.iterator = iterator;
        this.upperBound = upperBound;
        addHeader(TRANSFER_ENCODING_CHUNKED_HEADER);
    }
}
