package ok.dht.test.anikina.streaming;

import one.nio.http.Response;

public class ChunkedResponse extends Response {
    public ChunkedResponse() {
        super(OK);
        addHeader("Transfer-Encoding: chunked");
    }
}
