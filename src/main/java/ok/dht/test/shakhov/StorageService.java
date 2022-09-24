package ok.dht.test.shakhov;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.shakhov.storage.BaseEntry;
import ok.dht.test.shakhov.storage.Config;
import ok.dht.test.shakhov.storage.Dao;
import ok.dht.test.shakhov.storage.Entry;
import ok.dht.test.shakhov.storage.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import one.nio.util.Utf8;

public class StorageService implements Service {

    private final ServiceConfig config;
    private HttpServer server;
    private Dao<MemorySegment, Entry<MemorySegment>> dao;

    public StorageService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        dao = new MemorySegmentDao(new Config(java.nio.file.Path.of("/home/ishakhov/"), 4096));
        server = new HttpServer(createHttpServerConfigFromPort(config.selfPort())) {
            @Override
            public void handleDefault(Request request, HttpSession session) throws IOException {
                session.sendResponse(badRequest());
            }
        };
        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        dao.flush();
        dao.close();
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response handleGet(@Param(value = "id", required = true) String id) throws IOException {
        if (id.isEmpty()) {
            return badRequest();
        }
        MemorySegment key = MemorySegment.ofByteBuffer(ByteBuffer.wrap(Utf8.toBytes(id)));
        Entry<MemorySegment> entry = dao.get(key);
        if (entry == null) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }
        return new Response(Response.OK, entry.value().toByteArray());
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(@Param(value = "id", required = true) String id, Request request) {
        if (id.isEmpty()) {
            return badRequest();
        }
        MemorySegment key = MemorySegment.ofByteBuffer(ByteBuffer.wrap(Utf8.toBytes(id)));
        MemorySegment value = MemorySegment.ofByteBuffer(ByteBuffer.wrap(request.getBody()));
        Entry<MemorySegment> entry = new BaseEntry<>(key, value);
        dao.upsert(entry);
        return new Response(Response.CREATED, Response.EMPTY);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(@Param(value = "id", required = true) String id) {
        if (id.isEmpty()) {
            return badRequest();
        }
        MemorySegment key = MemorySegment.ofByteBuffer(ByteBuffer.wrap(Utf8.toBytes(id)));
        Entry<MemorySegment> entry = new BaseEntry<>(key, null);
        dao.upsert(entry);
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    private static HttpServerConfig createHttpServerConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    private static Response badRequest() {
        return new Response(Response.BAD_REQUEST, Response.EMPTY);
    }

    @ServiceFactory(stage = 1, week = 1)
    public static class StorageServiceFactory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new StorageService(config);
        }
    }
}
