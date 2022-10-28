package ok.dht.test.ilin.service;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.ilin.model.Entity;
import ok.dht.test.ilin.repository.EntityRepository;
import ok.dht.test.ilin.repository.impl.RocksDBEntityRepositoryImpl;
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

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public class EntityService implements Service {
    private final ServiceConfig config;
    private HttpServer server;
    private EntityRepository entityRepository;

    public EntityService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        server = new ExpandableHttpServer(createConfigFromPort(config.selfPort()));
        server.start();
        server.addRequestHandlers(this);
        entityRepository = new RocksDBEntityRepositoryImpl(config);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        entityRepository.close();
        server.stop();
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response getEntity(@Param(value = "id", required = true) String id) {
        if (id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        Entity entity = entityRepository.get(id);
        if (entity == null) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }
        return new Response(
            Response.OK,
            entity.value()
        );
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response upsertEntity(@Param(value = "id", required = true) String id, Request request) {
        byte[] body = request.getBody();
        if (id.isEmpty() || body == null) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        entityRepository.upsert(new Entity(id, body));
        return new Response(Response.CREATED, Response.EMPTY);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response deleteEntity(@Param(value = "id", required = true) String id) {
        if (id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        entityRepository.delete(id);
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

    public static class ExpandableHttpServer extends HttpServer {
        public ExpandableHttpServer(HttpServerConfig config, Object... routers) throws IOException {
            super(config, routers);
        }

        @Override
        public void handleDefault(Request request, HttpSession session) throws IOException {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
        }

        @Override
        public synchronized void stop() {
            Arrays.stream(selectors).forEach(it -> it.selector.forEach(Session::close));
            super.stop();
        }
    }

    @ServiceFactory(stage = 1, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new EntityService(config);
        }
    }
}
