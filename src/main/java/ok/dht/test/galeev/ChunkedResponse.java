package ok.dht.test.galeev;

import one.nio.http.Response;

import java.nio.ByteBuffer;
import java.util.Iterator;

public class ChunkedResponse extends Response {

    final Iterator<ByteBuffer> iterator;

    public ChunkedResponse(String resultCode, Iterator<ByteBuffer> iterator) {
        super(resultCode);
        super.addHeader("Transfer-Encoding: chunked");
        this.iterator = iterator;
    }
}
