package ok.dht.test.trofimov.dao;

import one.nio.http.Response;

import java.util.Iterator;

public class ChunkedResponse extends Response {

    private Iterator<Entry<String>> data;

    public ChunkedResponse(String resultCode, Iterator<Entry<String>> data) {
        super(resultCode);
        this.data = data;
        addHeader("Transfer-Encoding: chunked");
    }

    public Iterator<Entry<String>> getData() {
        return data;
    }
}
