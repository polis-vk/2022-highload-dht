package ok.dht.test.pobedonostsev;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.pobedonostsev.dao.BaseEntry;
import ok.dht.test.pobedonostsev.dao.Config;
import ok.dht.test.pobedonostsev.dao.Dao;
import ok.dht.test.pobedonostsev.dao.Entry;
import ok.dht.test.pobedonostsev.dao.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import one.nio.util.Utf8;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class DemoService implements Service {

    private static final long flushThresholdBytes = 1 << 20;
    private final ServiceConfig config;
    private HttpServer server;
    private Dao<MemorySegment, Entry<MemorySegment>> dao;

    public DemoService(ServiceConfig config) {
        this.config = config;
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[] {acceptor};
        return httpConfig;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        dao = new MemorySegmentDao(new Config(config.workingDir(), flushThresholdBytes));
        server = new CustomHttpServer(createConfigFromPort(config.selfPort()));
        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        dao.close();
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response getEntry(@Param("id") String key) throws IOException {
        if (key == null || key.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        Entry<MemorySegment> entry = dao.get(MemorySegment.ofArray(Utf8.toBytes(key)));
        if (entry == null) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }
        return Response.ok(entry.value().toByteArray());
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response upsertEntry(Request request, @Param("id") String key) {
        if (key == null || key.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        MemorySegment value = MemorySegment.ofArray(request.getBody());
        dao.upsert(new BaseEntry<>(MemorySegment.ofArray(Utf8.toBytes(key)), value));
        return new Response(Response.CREATED, Response.EMPTY);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response deleteEntry(@Param("id") String key) {
        if (key == null || key.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        dao.upsert(new BaseEntry<>(MemorySegment.ofArray(Utf8.toBytes(key)), null));
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    @ServiceFactory(stage = 1, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new DemoService(config);
        }
    }
}
