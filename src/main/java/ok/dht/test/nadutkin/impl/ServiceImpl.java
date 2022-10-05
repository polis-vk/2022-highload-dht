package ok.dht.test.nadutkin.impl;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.nadutkin.database.BaseEntry;
import ok.dht.test.nadutkin.database.Config;
import ok.dht.test.nadutkin.database.Entry;
import ok.dht.test.nadutkin.database.impl.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static ok.dht.test.nadutkin.impl.UtilsClass.getBytes;

public class ServiceImpl implements Service {

    private static final String PATH = "/v0/entity";
    private final ServiceConfig config;
    private HttpServer server;
    private MemorySegmentDao dao;

    public ServiceImpl(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        long flushThresholdBytes = 1 << 18;
        dao = new MemorySegmentDao(new Config(config.workingDir(), flushThresholdBytes));
        server = new HighLoadHttpServer(createConfigFromPort(config.selfPort()));
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

    private MemorySegment getKey(String id) {
        return MemorySegment.ofArray(getBytes(id));
    }

    @Path(PATH)
    @RequestMethod(Request.METHOD_GET)
    public Response get(@Param(value = "id", required = true) String id) {
        if (id == null || id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, getBytes("Id can not be null or empty!"));
        } else {
            Entry<MemorySegment> value = dao.get(getKey(id));
            if (value == null) {
                return new Response(Response.NOT_FOUND, getBytes("Can't find any value, for id %1$s".formatted(id)));
            } else {
                return new Response(Response.OK, value.value().toByteArray());
            }
        }
    }

    private Response upsert(String id, MemorySegment value, String goodResponse) {
        if (id == null || id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, getBytes("Id can not be null or empty!"));
        } else {
            MemorySegment key = getKey(id);
            Entry<MemorySegment> entry = new BaseEntry<>(key, value);
            dao.upsert(entry);
            return new Response(goodResponse, Response.EMPTY);
        }
    }

    @Path(PATH)
    @RequestMethod(Request.METHOD_PUT)
    public Response put(@Param(value = "id", required = true) String id,
                        @Param(value = "request", required = true) Request request) {
        return upsert(id, MemorySegment.ofArray(request.getBody()), Response.CREATED);
    }

    @Path(PATH)
    @RequestMethod({Request.METHOD_CONNECT,
            Request.METHOD_HEAD,
            Request.METHOD_OPTIONS,
            Request.METHOD_PATCH,
            Request.METHOD_POST,
            Request.METHOD_TRACE})
    public Response others() {
        return new Response(Response.METHOD_NOT_ALLOWED, getBytes("Not implemented yet"));
    }

    @Path(PATH)
    @RequestMethod(Request.METHOD_DELETE)
    public Response delete(@Param(value = "id", required = true) String id) {
        return upsert(id, null, Response.ACCEPTED);
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @ServiceFactory(stage = 1, week = 1, bonuses = {"SingleNodeTest#respectFileFolder"})
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
