package ok.dht.test.mikhaylov;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.mikhaylov.dao.BaseEntry;
import ok.dht.test.mikhaylov.dao.Config;
import ok.dht.test.mikhaylov.dao.Entry;
import ok.dht.test.mikhaylov.dao.artyomdrozdov.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class MyService implements Service {

    private final ServiceConfig config;
    private HttpServer server;

    private MemorySegmentDao dao;

    public MyService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        server = new HttpServer(createServerConfigFromPort(config.selfPort())) {
            @Override
            public void handleDefault(Request request, HttpSession session) throws IOException {
                session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            }

            @Override
            public synchronized void stop() {
                for (SelectorThread selectorThread : selectors) {
                    for (Session session : selectorThread.selector) {
                        session.close();
                    }
                }
                super.stop();
            }
        };
        server.addRequestHandlers(this);
        server.start();
        int flushThresholdBytes = 1024 * 1024;
        dao = new MemorySegmentDao(new Config(config.workingDir(), flushThresholdBytes));
        return CompletableFuture.completedFuture(null);
    }

    private static HttpServerConfig createServerConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        server = null;
        dao.close();
        dao = null;
        return CompletableFuture.completedFuture(null);
    }

    private static byte[] strToBytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static MemorySegment strToSegment(String s) {
        return MemorySegment.ofArray(strToBytes(s));
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response handleGet(@Param(value = "id", required = true) final String id) {
        if (id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, strToBytes("Empty id"));
        }
        Entry<MemorySegment> entry = dao.get(strToSegment(id));
        if (entry == null) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }
        return new Response(Response.OK, entry.value().toByteArray());
    }

    private Response upsert(String id, MemorySegment newValue, Response onSuccess) {
        if (id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, strToBytes("Empty id"));
        }
        dao.upsert(new BaseEntry<>(strToSegment(id), newValue));
        return onSuccess;
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(@Param(value = "id", required = true) final String id, final Request request) {
        return upsert(id, MemorySegment.ofArray(request.getBody()), new Response(Response.CREATED, Response.EMPTY));
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(@Param(value = "id", required = true) final String id) {
        return upsert(id, null, new Response(Response.ACCEPTED, Response.EMPTY));
    }

    @ServiceFactory(stage = 1, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new MyService(config);
        }
    }
}
