package ok.dht.test.armenakyan;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.armenakyan.util.ServiceUtils;
import one.nio.http.HttpServer;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.util.Utf8;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

public class DhtService implements Service {

    private static final String DB_NAME = "rocks";
    private static final String ID_PARAM = "id=";
    private final ServiceConfig serviceConfig;
    private HttpServer httpServer;
    private RocksDB rocksDB;

    static {
        RocksDB.loadLibrary();
    }

    public DhtService(ServiceConfig serviceConfig) {
        this.serviceConfig = serviceConfig;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        httpServer = new DhtHttpServer(ServiceUtils.createConfigFromPort(serviceConfig.selfPort()), this);

        Files.createDirectories(serviceConfig.workingDir());
        try {
            rocksDB = RocksDB.open(serviceConfig.workingDir().resolve(DB_NAME).toString());
        } catch (RocksDBException e) {
            throw new IOException(e);
        }

        httpServer.start();

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        httpServer.stop();
        try {
            rocksDB.closeE();
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
        httpServer = null;
        rocksDB = null;
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    public Response handle(Request request) {
        String id = request.getParameter(ID_PARAM);
        if (id == null || id.isBlank()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }

        try {
            switch (request.getMethod()) {
                case Request.METHOD_GET:
                    return get(id);
                case Request.METHOD_PUT:
                    return put(id, request.getBody());
                case Request.METHOD_DELETE:
                    return delete(id);
                default:
                    return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            }
        } catch (RocksDBException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }

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

    @ServiceFactory(stage = 1, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig serviceConfig) {
            return new DhtService(serviceConfig);
        }
    }
}
