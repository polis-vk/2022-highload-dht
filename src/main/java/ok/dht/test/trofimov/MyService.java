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

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class MyService implements Service {

    private static final String CONNECTION_CLOSE = "Connection: close";
    private final ServiceConfig config;
    private HttpServer server;
    private InMemoryDao dao;

    public MyService(ServiceConfig config) {
        this.config = config;
    }

    private Config createDaoConfig() {
        long flushThreshold = 1 << 20;
        return new Config(config.workingDir(), flushThreshold);
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

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response handleGet(@Param(value = "id", required = true) String id) throws IOException {
        if (id.isEmpty()) {
            return getCloseReponse(Response.BAD_REQUEST, Response.EMPTY);
        }
        Entry<String> entry = dao.get(id);
        if (entry == null) {
            return getCloseReponse(Response.NOT_FOUND, Response.EMPTY);
        }
        String value = entry.value();
        char[] chars = value.toCharArray();

        return getCloseReponse(Response.OK, Base64.decodeFromChars(chars));

    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(Request request, @Param(value = "id", required = true) String id) {
        if (id.isEmpty()) {
            return getCloseReponse(Response.BAD_REQUEST, Response.EMPTY);
        }

        byte[] value = request.getBody();
        dao.upsert(new BaseEntry<>(id, new String(Base64.encodeToChars(value))));
        return getCloseReponse(Response.CREATED, Response.EMPTY);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(@Param(value = "id", required = true) String id) {
        if (id.isEmpty()) {
            return getCloseReponse(Response.BAD_REQUEST, Response.EMPTY);
        }
        dao.upsert(new BaseEntry<>(id, null));
        return getCloseReponse(Response.ACCEPTED, Response.EMPTY);
    }

    private Response getCloseReponse(String status, byte[] body) {
        Response response = new Response(status, body);
        response.addHeader(CONNECTION_CLOSE);
        return response;
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @ServiceFactory(stage = 1, week = 1)
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new MyService(config);
        }
    }
}

