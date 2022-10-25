package ok.dht.test.armenakyan.distribution;

import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.util.Utf8;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SelfNodeHandler implements NodeRequestHandler {
    private static final String DB_NAME = "rocks";
    private final RocksDB rocksDB;

    static {
        RocksDB.loadLibrary();
    }

    public SelfNodeHandler(Path dbWorkingDir) throws IOException {
        Files.createDirectories(dbWorkingDir);
        try {
            rocksDB = RocksDB.open(dbWorkingDir.resolve(DB_NAME).toString());
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }

    @Override
    public Response handleForKey(String id, Request request) {
        try {
            return switch (request.getMethod()) {
                case Request.METHOD_PUT -> put(id, request.getBody());
                case Request.METHOD_GET -> get(id);
                case Request.METHOD_DELETE -> delete(id);
                default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            };
        } catch (RocksDBException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    @Override
    public void handleForKey(String key, Request request, HttpSession session) throws IOException {
        session.sendResponse(handleForKey(key, request));
    }

    public Response get(String id) throws RocksDBException {
        byte[] bytes = rocksDB.get(Utf8.toBytes(id));
        if (bytes == null) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }

        return Response.ok(bytes);
    }

    public Response put(String id, byte[] body) throws RocksDBException {
        rocksDB.put(Utf8.toBytes(id), body);

        return new Response(Response.CREATED, Response.EMPTY);
    }

    public Response delete(String id) throws RocksDBException {
        rocksDB.delete(Utf8.toBytes(id));

        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    @Override
    public void close() throws IOException {
        try {
            rocksDB.closeE();
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
    }
}
