package ok.dht.test.armenakyan.distribution;

import ok.dht.test.armenakyan.distribution.model.Value;
import ok.dht.test.armenakyan.util.ServiceUtils;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.util.Utf8;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

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
    public void handleForKey(String key, Request request, HttpSession session, long timestamp) throws IOException {
        session.sendResponse(handleForKey(key, request, timestamp));
    }

    private Response handleForKey(String id, Request request, long timestamp) {
        String timestampHeader = request.getHeader(ServiceUtils.TIMESTAMP_HEADER.concat(": "));

        boolean isProxied = timestamp == -1;
        if (timestampHeader != null && isProxied) {
            try {
                timestamp = Long.parseLong(timestampHeader);

                if (timestamp < 0) {
                    return new Response(Response.BAD_REQUEST, Response.EMPTY);
                }
            } catch (NumberFormatException ignored) {
                return new Response(Response.BAD_REQUEST, Response.EMPTY);
            }
        }

        try {
            return switch (request.getMethod()) {
                case Request.METHOD_PUT -> put(id, request.getBody(), timestamp);
                case Request.METHOD_GET -> get(id, timestamp);
                case Request.METHOD_DELETE -> delete(id, timestamp);
                default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            };
        } catch (RocksDBException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    @Override
    public CompletableFuture<Response> handleForKeyAsync(String key, Request request, long timestamp) {
        return CompletableFuture.supplyAsync(() -> handleForKey(key, request, timestamp));
    }

    public Response get(String id, long timestamp) throws RocksDBException {
        byte[] bytes = rocksDB.get(Utf8.toBytes(id));

        if (bytes == null) {
            return new Response(Response.NOT_FOUND, Value.tombstone(timestamp).toBytes());
        }

        Value value = Value.fromBytes(bytes);
        if (value.isTombstone()) {
            return new Response(Response.NOT_FOUND, value.toBytes());
        }

        return Response.ok(value.toBytes());
    }

    public Response put(String id, byte[] body, long timestamp) throws RocksDBException {
        Value value = new Value(body, timestamp);
        rocksDB.put(Utf8.toBytes(id), value.toBytes());

        return new Response(Response.CREATED, Response.EMPTY);
    }

    public Response delete(String id, long timestamp) throws RocksDBException {
        rocksDB.put(Utf8.toBytes(id), Value.tombstone(timestamp).toBytes());

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
