package ok.dht.test.yasevich;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.yasevich.dao.BaseEntry;
import ok.dht.test.yasevich.dao.Config;
import ok.dht.test.yasevich.dao.Dao;
import ok.dht.test.yasevich.dao.Entry;
import ok.dht.test.yasevich.artyomdrozdov.MemorySegmentDao;
import one.nio.http.*;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class ServiceImpl implements Service {
    private final ServiceConfig config;
    private Dao<MemorySegment, Entry<MemorySegment>> dao;
    private HttpServer server;

    public ServiceImpl(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        dao = new MemorySegmentDao(new Config(config.workingDir(), 100 * 1024 * 1024));
        server = new CustomHttpServer(createConfigFromPort(config.selfPort()));
        server.start();
        server.addRequestHandlers(this);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        if (server != null) {
            server.stop();
        }
        if (dao != null) {
            dao.close();
        }
        return CompletableFuture.completedFuture(null);
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }


    @Path("/v0/entity")
    public Response handleRequest(@Param(value = "id", required = true) String id, Request request) throws IOException {
        if (id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        return switch (request.getMethod()) {
            case Request.METHOD_GET -> handleGet(id);
            case Request.METHOD_PUT -> handlePost(id, request);
            case Request.METHOD_DELETE -> handleDelete(id);
            default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
        };
    }

    private Response handleGet(String id) throws IOException {
        Entry<MemorySegment> entry = dao.get(fromString(id));
        if (entry == null) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }
        return new Response(Response.OK, entry.value().toByteArray());
    }


    private Response handleDelete(String id) {
        dao.upsert(new BaseEntry<>(fromString(id), null));
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }


    private Response handlePost(String id, Request request) {
        dao.upsert(new BaseEntry<>(fromString(id), MemorySegment.ofArray(request.getBody())));
        return new Response(Response.CREATED, Response.EMPTY);
    }

    private static MemorySegment fromString(String data) {
        return data == null ? null : MemorySegment.ofArray(data.toCharArray());
    }

    private static class CustomHttpServer extends HttpServer {

        public CustomHttpServer(HttpServerConfig config, Object... routers) throws IOException {
            super(config, routers);
        }

        @Override
        public void handleDefault(Request request, HttpSession session) throws IOException {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
        }

        @Override
        public synchronized void stop() {
            for (SelectorThread thread : selectors) {
                for (Session session : thread.selector) {
                    session.socket().close();
                }
            }
            super.stop();
        }

    }

    @ServiceFactory(stage = 1, week = 1)
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }

}
