package ok.dht.test.armenakyan.chunk;

import ok.dht.test.armenakyan.dao.model.Entity;
import one.nio.http.Response;

import java.util.Iterator;

public class ChunkedResponse extends Response {
    private static final String CHUNKED_ENCODING_HEADER = "Transfer-Encoding: chunked";
    private final Iterator<Entity> entitiesIterator;

    public ChunkedResponse(String resultCode, Iterator<Entity> entitiesIterator) {
        super(resultCode, Response.EMPTY);
        getHeaders()[1] = (CHUNKED_ENCODING_HEADER);
        this.entitiesIterator = entitiesIterator;
    }

    public Iterator<Entity> entitiesIterator() {
        return entitiesIterator;
    }
}
