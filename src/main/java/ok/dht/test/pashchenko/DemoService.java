package ok.dht.test.pashchenko;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.pashchenko.dao.Config;
import ok.dht.test.pashchenko.dao.Entry;
import ok.dht.test.pashchenko.dao.MemorySegmentDao;
import one.nio.http.*;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import one.nio.util.Utf8;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class DemoService implements Service {

    private final ServiceConfig config;
    private HttpServer server;
    private MemorySegmentDao dao;

    public DemoService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        dao = new MemorySegmentDao(new Config(config.workingDir(), 1048576L));
        server = new HttpServer(createConfigFromPort(config.selfPort())) {
            @Override
            public void handleDefault(Request request, HttpSession session) throws IOException {
                Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
                session.sendResponse(response);
            }

            @Override
            public synchronized void stop() {
                super.stop();
                for (SelectorThread selector : selectors) {
                    for (Session session : selector.selector) {
                        session.close();
                    }
                }
            }
        };
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
    public Response handleGet(@Param(value = "id") String id) {
        if (id == null || id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        Entry entry = dao.get(MemorySegment.ofArray(Utf8.toBytes(id)));
        if (entry == null) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }
        return new Response(Response.OK, entry.value().toByteArray());
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(Request request, @Param(value = "id") String id) {
        if (id == null || id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        dao.upsert(new Entry(MemorySegment.ofArray(Utf8.toBytes(id)), MemorySegment.ofArray(request.getBody())));
        return new Response(Response.CREATED, Response.EMPTY);
    }


    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(@Param(value = "id", required = true) String id) {
        if (id == null || id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        dao.upsert(new Entry(MemorySegment.ofArray(Utf8.toBytes(id)), null));
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }


    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @ServiceFactory(stage = 1, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new DemoService(config);
        }
    }
}
