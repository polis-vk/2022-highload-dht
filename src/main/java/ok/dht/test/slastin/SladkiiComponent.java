package ok.dht.test.slastin;

import ok.dht.test.slastin.range.RangeResponse;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.util.Utf8;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Slice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.nio.ByteBuffer;

import static ok.dht.test.slastin.Utils.accepted;
import static ok.dht.test.slastin.Utils.created;
import static ok.dht.test.slastin.Utils.internalError;
import static ok.dht.test.slastin.Utils.notFound;

public class SladkiiComponent implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(SladkiiComponent.class);

    private RocksDB db;

    private boolean isClosed;

    public SladkiiComponent(Options options, String location) {
        try {
            db = RocksDB.open(options, location);
        } catch (RocksDBException e) {
            log.error("Can not open RocksDB by {}", location, e);
        }
        isClosed = false;
    }

    public Response get(String id) {
        try {
            byte[] entry = db.get(Utf8.toBytes(id));
            return entry == null ? notFound() : new Response(Response.OK, entry);
        } catch (RocksDBException e) {
            log.error("get(id=\"{}\")", id, e);
            return internalError();
        }
    }

    public Response put(String id, long timestamp, Request request) {
        byte[] value = request.getBody();

        ByteBuffer entry = ByteBuffer.allocate(Long.BYTES + 1 + value.length);
        entry.putLong(timestamp);
        entry.put((byte) 1); // isAlive?
        entry.put(value);

        try {
            db.put(Utf8.toBytes(id), entry.array());
            return created();
        } catch (RocksDBException e) {
            log.error("put(id=\"{}\")", id, e);
            return internalError();
        }
    }

    public Response delete(String id, long timestamp) {
        ByteBuffer entry = ByteBuffer.allocate(Long.BYTES + 1);
        entry.putLong(timestamp);
        entry.put((byte) 0);

        try {
            db.put(Utf8.toBytes(id), entry.array());
            return accepted();
        } catch (RocksDBException e) {
            log.error("delete(id=\"{}\")", id, e);
            return internalError();
        }
    }

    public RangeResponse range(String start, String end) {
        var readOptions = new ReadOptions();
        if (end != null) {
            readOptions.setIterateUpperBound(new Slice(end));
        }

        RocksIterator rangeIterator = db.newIterator(readOptions);
        if (start != null) {
            rangeIterator.seek(Utf8.toBytes(start));
        }

        return new RangeResponse(rangeIterator);
    }

    @Override
    public void close() {
        if (!isClosed) {
            db.close();
            db = null;

            isClosed = true;
        }
    }
}
