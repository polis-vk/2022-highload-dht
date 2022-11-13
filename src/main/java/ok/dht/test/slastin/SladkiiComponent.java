package ok.dht.test.slastin;

import one.nio.http.Request;
import one.nio.http.Response;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;

import static ok.dht.test.slastin.SladkiiServer.accepted;
import static ok.dht.test.slastin.SladkiiServer.created;
import static ok.dht.test.slastin.SladkiiServer.internalError;
import static ok.dht.test.slastin.SladkiiServer.notFound;

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
            byte[] value = db.get(toBytes(id));
            return value == null ? notFound() : new Response(Response.OK, value);
        } catch (RocksDBException e) {
            log.error("get(id=\"{}\")", id, e);
            return internalError();
        }
    }

    public Response put(String id, Request request) {
        try {
            db.put(toBytes(id), request.getBody());
            return created();
        } catch (RocksDBException e) {
            log.error("put(id=\"{}\")", id, e);
            return internalError();
        }
    }

    public Response delete(String id) {
        try {
            db.delete(toBytes(id));
            return accepted();
        } catch (RocksDBException e) {
            log.error("delete(id=\"{}\")", id, e);
            return internalError();
        }
    }

    private static byte[] toBytes(String value) {
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
