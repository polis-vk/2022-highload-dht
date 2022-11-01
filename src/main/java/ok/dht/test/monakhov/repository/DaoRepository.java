package ok.dht.test.monakhov.repository;

import ok.dht.test.monakhov.model.EntryWrapper;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.util.Utf8;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.IOException;
import java.sql.Timestamp;

import static ok.dht.test.monakhov.utils.ServiceUtils.responseAccepted;
import static ok.dht.test.monakhov.utils.ServiceUtils.responseCreated;
import static ok.dht.test.monakhov.utils.ServiceUtils.responseInternalError;
import static ok.dht.test.monakhov.utils.ServiceUtils.responseMethodNotAllowed;
import static ok.dht.test.monakhov.utils.ServiceUtils.responseNotFound;
import static ok.dht.test.monakhov.utils.ServiceUtils.responseOk;
import static one.nio.serial.Serializer.serialize;

public class DaoRepository implements AutoCloseable {
    private static final Log log = LogFactory.getLog(DaoRepository.class);
    private final RocksDB dao;

    public DaoRepository(String dir) throws IOException {
        try {
            dao = RocksDB.open(dir);
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }

    public Response executeDaoOperation(String id, Request request, Timestamp timestamp) {
        try {
            EntryWrapper entry = new EntryWrapper(request, timestamp);
            return executeDaoOperation(id, request, serialize(entry));
        } catch (IOException e) {
            log.error("Exception occurred while serialization", e);
            return responseInternalError();
        }
    }

    private Response executeDaoOperation(String id, Request request, byte[] body) {
        try {
            return switch (request.getMethod()) {
                case Request.METHOD_GET -> getEntity(id);
                case Request.METHOD_PUT -> putEntity(id, body);
                case Request.METHOD_DELETE -> deleteEntity(id, body);
                default -> responseMethodNotAllowed();
            };
        } catch (RocksDBException e) {
            log.error("Exception occurred in database interaction", e);
            return responseInternalError();
        }
    }

    private Response getEntity(String id) throws RocksDBException {
        final var entry = dao.get(Utf8.toBytes(id));

        if (entry == null) {
            return responseNotFound();
        }

        return responseOk(entry);
    }

    private Response putEntity(String id, byte[] body) throws RocksDBException {
        dao.put(Utf8.toBytes(id), body);

        return responseCreated();
    }

    private Response deleteEntity(String id, byte[] body) throws RocksDBException {
        dao.put(Utf8.toBytes(id), body);

        return responseAccepted();
    }

    @Override
    public void close() throws IOException {
        try {
            dao.syncWal();
            dao.closeE();
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }
}
