package ok.dht.test.slastin;

import one.nio.http.Request;
import one.nio.http.Response;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

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
            byte[] entry = db.get(toBytes(id));
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
            db.put(toBytes(id), entry.array());
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
            db.put(toBytes(id), entry.array());
            return accepted();
        } catch (RocksDBException e) {
            log.error("delete(id=\"{}\")", id, e);
            return internalError();
        }
    }

    static byte[] toBytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
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
