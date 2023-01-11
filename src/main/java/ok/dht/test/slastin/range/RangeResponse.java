package ok.dht.test.slastin.range;

import one.nio.http.Response;
import one.nio.util.Utf8;
import org.rocksdb.RocksIterator;

public class RangeResponse extends Response {
    public static final byte[] HTTP11_HEADER = Utf8.toBytes("HTTP/1.1 ");
    public static final int PROTOCOL_HEADER_LENGTH = 11;

    private final RocksIterator rangeIterator;

    public RangeResponse(RocksIterator rangeIterator) {
        super(Response.OK);
        this.rangeIterator = rangeIterator;
    }

    public RocksIterator getRangeIterator() {
        return rangeIterator;
    }

    public RangeQueueItem toRangeQueueItem() {
        addHeader("Transfer-Encoding: chunked");
        return new RangeQueueItem(this);
    }

}
