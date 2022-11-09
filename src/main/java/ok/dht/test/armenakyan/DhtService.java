package ok.dht.test.armenakyan;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.armenakyan.util.ServiceUtils;
import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.util.Utf8;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

public class DhtService implements Service {
    private static final String DB_NAME = "rocks";
    private static final String ID_PARAM = "id=";
    private final ServiceConfig serviceConfig;
    private HttpServer httpServer;

    private ForkJoinPool workerPool;

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

        workerPool = new ForkJoinPool(
                Runtime.getRuntime().availableProcessors(),
                ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                null,
                true
        );

        httpServer.start();

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        httpServer.stop();
        workerPool.shutdownNow();
        try {
            rocksDB.closeE();
        } catch (RocksDBException e) {
            throw new IOException(e);
        }

        httpServer = null;
        rocksDB = null;
        workerPool = null;
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    public void handle(Request request, HttpSession session) {
        workerPool.execute(() -> {
            try {
                try {
                    String id = request.getParameter(ID_PARAM);
                    if (id == null || id.isBlank()) {
                        session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                        return;
                    }

                    session.sendResponse(
                            switch (request.getMethod()) {
                                case Request.METHOD_GET -> get(id);
                                case Request.METHOD_PUT -> put(id, request.getBody());
                                case Request.METHOD_DELETE -> delete(id);
                                default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
                            }
                    );
                } catch (RocksDBException e) {
                    session.sendResponse(new Response(Response.INTERNAL_ERROR, Response.EMPTY));
                }
            } catch (IOException e) {
                session.handleException(e);
            }
        });
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

    @ServiceFactory(stage = 2, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig serviceConfig) {
            return new DhtService(serviceConfig);
        }
    }
}
