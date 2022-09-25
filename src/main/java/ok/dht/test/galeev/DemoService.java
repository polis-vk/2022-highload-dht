package ok.dht.test.galeev;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.galeev.dao.DaoMiddleLayer;
import ok.dht.test.galeev.dao.entry.Entry;
import ok.dht.test.galeev.dao.utils.DaoConfig;
import ok.dht.test.galeev.dao.utils.StringByteConverter;
import one.nio.http.*;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;

import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

public class DemoService implements Service {

    public static final int FLUSH_THRESHOLD_BYTES = 1048576;
    public static final String DAO_DIRECTORY = "dao";
    public static final Response BAD_RESPONSE = new Response(Response.BAD_REQUEST, Response.EMPTY);
    public static final Response NOT_FOUND_RESPONSE = new Response(Response.NOT_FOUND, Response.EMPTY);
    private final ServiceConfig config;
    private HttpServer server;
    private DaoMiddleLayer<String, byte[]> dao;

    public DemoService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        dao = getDao(config);
        server = getCloseableServer(config.selfPort());
        server.start();
        server.addRequestHandlers(this);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        dao.stop();
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response handleGet(@Param(value = "id", required = true) String id) throws IOException {
        if (id.isEmpty()) {
            return BAD_RESPONSE;
        }

        Entry<String, byte[]> entry = dao.get(id);
        if (entry == null) {
            return NOT_FOUND_RESPONSE;
        }
        return new Response(Response.OK, entry.value());
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(Request request, @Param(value = "id", required = true) String id) {
        if (id.isEmpty()) {
            return BAD_RESPONSE;
        }
        dao.upsert(id, request.getBody());
        return new Response(Response.CREATED, Response.EMPTY);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(@Param(value = "id", required = true) String id) {
        if (id.isEmpty()) {
            return BAD_RESPONSE;
        }
        dao.delete(id);
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    private static DaoMiddleLayer<String, byte[]> getDao(ServiceConfig config) throws IOException {
        if (!Files.exists(config.workingDir())) {
            Files.createDirectory(config.workingDir());
        }
        return new DaoMiddleLayer<>(
                new DaoConfig(
                        config.workingDir(),
                        FLUSH_THRESHOLD_BYTES //1MB
                ),
                new StringByteConverter()
        );
    }

    private static HttpServer getCloseableServer(final int port) throws IOException {
        return new HttpServer(createConfigFromPort(port)) {
            @Override
            public void handleDefault(Request request, HttpSession session) throws IOException {
                session.sendResponse(BAD_RESPONSE);
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
        };
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
            return new DemoService(config);
        }
    }
}
