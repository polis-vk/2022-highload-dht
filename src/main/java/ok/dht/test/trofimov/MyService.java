package ok.dht.test.trofimov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.trofimov.dao.BaseEntry;
import ok.dht.test.trofimov.dao.Config;
import ok.dht.test.trofimov.dao.Entry;
import ok.dht.test.trofimov.dao.MyHttpServer;
import ok.dht.test.trofimov.dao.impl.InMemoryDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import one.nio.util.Base64;
import one.nio.util.Utf8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class MyService implements Service {

    public static final String PATH_V0_ENTITY = "/v0/entity";
    private final Logger logger = LoggerFactory.getLogger(MyService.class);
    private static final long FLUSH_THRESHOLD = 1 << 20;
    private final ServiceConfig config;
    private HttpServer server;
    private InMemoryDao dao;

    public MyService(ServiceConfig config) {
        this.config = config;
    }

    private Config createDaoConfig() {
        return new Config(config.workingDir(), FLUSH_THRESHOLD);
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        dao = new InMemoryDao(createDaoConfig());
        server = new MyHttpServer(createConfigFromPort(config.selfPort()));
        server.start();
        server.addRequestHandlers(this);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        dao.close();
        return CompletableFuture.completedFuture(null);
    }

    @Path(PATH_V0_ENTITY)
    @RequestMethod(Request.METHOD_GET)
    public Response handleGet(@Param(value = "id", required = true) String id) throws IOException {
        if (id.isEmpty()) {
            return emptyResponseFor(Response.BAD_REQUEST);
        }

        try {
            Entry<String> entry = dao.get(id);
            if (entry == null) {
                return emptyResponseFor(Response.NOT_FOUND);
            }
            String value = entry.value();
            char[] chars = value.toCharArray();
            return new Response(Response.OK, Base64.decodeFromChars(chars));
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    @Path(PATH_V0_ENTITY)
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(Request request, @Param(value = "id", required = true) String id) {
        if (id.isEmpty()) {
            return emptyResponseFor(Response.BAD_REQUEST);
        }

        byte[] value = request.getBody();
        try {
            dao.upsert(new BaseEntry<>(id, new String(Base64.encodeToChars(value))));
        } catch (Exception e) {
            return errorResponse(e);
        }
        return emptyResponseFor(Response.CREATED);
    }

    @Path(PATH_V0_ENTITY)
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(@Param(value = "id", required = true) String id) {
        if (id.isEmpty()) {
            return emptyResponseFor(Response.BAD_REQUEST);
        }
        try {
            dao.upsert(new BaseEntry<>(id, null));
        } catch (Exception e) {
            return errorResponse(e);
        }
        return emptyResponseFor(Response.ACCEPTED);
    }

    @Path(PATH_V0_ENTITY)
    @RequestMethod(Request.METHOD_POST)
    public Response handlePost() {
        return emptyResponseFor(Response.METHOD_NOT_ALLOWED);
    }

    private Response emptyResponseFor(String status) {
        return new Response(status, Response.EMPTY);
    }

    private Response errorResponse(Exception e) {
        logger.error("Error while process request", e);
        return new Response(Response.INTERNAL_ERROR, Utf8.toBytes(e.toString()));
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @ServiceFactory(stage = 1, week = 2, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new MyService(config);
        }
    }
}

