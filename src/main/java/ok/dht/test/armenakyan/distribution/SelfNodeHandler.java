package ok.dht.test.armenakyan.distribution;

import ok.dht.test.armenakyan.dao.DaoException;
import ok.dht.test.armenakyan.dao.RocksDBDao;
import ok.dht.test.armenakyan.dao.model.Value;
import ok.dht.test.armenakyan.util.ServiceUtils;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import org.rocksdb.RocksDB;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class SelfNodeHandler implements NodeRequestHandler {
    private static final String TIMESTAMP_HEADER = ServiceUtils.TIMESTAMP_HEADER.concat(": ");
    private final RocksDBDao rocksDao;
    private final ExecutorService workerPool;

    static {
        RocksDB.loadLibrary();
    }

    public SelfNodeHandler(Path dbWorkingDir, ExecutorService workerPool) throws IOException {
        Files.createDirectories(dbWorkingDir);
        try {
            this.rocksDao = new RocksDBDao(dbWorkingDir);
            this.workerPool = workerPool;
        } catch (DaoException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void handleForKey(String key, Request request, HttpSession session, long timestamp) throws IOException {
        session.sendResponse(handleForKey(key, request, timestamp));
    }

    private Response handleForKey(String id, Request request, long timestamp) {
        String timestampHeader = request.getHeader(TIMESTAMP_HEADER);

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
                case Request.METHOD_GET -> get(id);
                case Request.METHOD_DELETE -> delete(id, timestamp);
                default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            };
        } catch (DaoException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    @Override
    public CompletableFuture<Response> handleForKeyAsync(String key, Request request, long timestamp) {
        return CompletableFuture.supplyAsync(() -> handleForKey(key, request, timestamp), workerPool);
    }

    public Response get(String id) throws DaoException {
        Value value = rocksDao.get(id);

        if (value.isTombstone()) {
            return new Response(Response.NOT_FOUND, value.toBytes());
        }

        return Response.ok(value.toBytes());
    }

    public Response put(String id, byte[] body, long timestamp) throws DaoException {
        rocksDao.put(id, body, timestamp);

        return new Response(Response.CREATED, Response.EMPTY);
    }

    public Response delete(String id, long timestamp) throws DaoException {
        rocksDao.delete(id, timestamp);

        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    @Override
    public void close() throws IOException {
        try {
            workerPool.shutdownNow();
            rocksDao.close();
        } catch (DaoException e) {
            throw new IOException(e);
        }
    }

}
