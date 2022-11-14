package ok.dht.test.monakhov;

import one.nio.http.Response;
import org.rocksdb.RocksIterator;

public class ChunkedResponse extends Response {
    public RocksIterator iterator;

    public ChunkedResponse(String resultCode, RocksIterator iterator) {
        super(resultCode, Response.EMPTY);

        this.iterator = iterator;
    }
}
