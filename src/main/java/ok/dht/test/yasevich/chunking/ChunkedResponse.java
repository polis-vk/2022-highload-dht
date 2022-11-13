package ok.dht.test.yasevich.chunking;

import ok.dht.test.yasevich.dao.Entry;
import one.nio.http.Response;

import java.util.Iterator;

public class ChunkedResponse extends Response {
    public final Iterator<Entry<byte[]>> entries;

    public ChunkedResponse(String resultCode, Iterator<Entry<byte[]>> entries) {
        super(resultCode, Response.EMPTY);
        getHeaders()[1] = "Transfer-Encoding: chunked";
        this.entries = entries;
    }
}
