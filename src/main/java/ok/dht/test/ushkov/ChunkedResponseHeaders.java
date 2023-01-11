package ok.dht.test.ushkov;

import one.nio.http.Response;

public class ChunkedResponseHeaders extends Response {
    public ChunkedResponseHeaders(String resultCode) {
        super(resultCode, new byte[0]);
        getHeaders()[1] = "Transfer-Encoding: chunked";
    }
}
