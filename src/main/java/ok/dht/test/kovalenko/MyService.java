package ok.dht.test.kovalenko;

import ok.dht.Dao;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.kovalenko.dao.LSMDao;
import ok.dht.kovalenko.dao.aliases.TypedBaseEntry;
import ok.dht.kovalenko.dao.aliases.TypedEntry;
import ok.dht.test.ServiceFactory;
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import javax.annotation.CheckForNull;

public class MyService implements Service {

    private final ServiceConfig config;
    private LSMDao dao;
    private HttpServer server;
    private static final ByteBufferDaoFactory daoFactory = new ByteBufferDaoFactory();

    public MyService(ServiceConfig config) throws IOException {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        this.dao = new LSMDao(config);
        server = new HttpServer(createConfigFromPort(config.selfPort()));
        server.start();
        server.addRequestHandlers(this);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        this.dao.close();
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response handleGet(@Param(value = "id", required = true) String id) {
        try {
            if (id.isEmpty()) {
                return new Response(Response.BAD_REQUEST, Response.EMPTY);
            }
            ByteBuffer key = daoFactory.fromString(id);
            TypedEntry response = dao.get(key);
            if (response == null) {
                return new Response(Response.NOT_FOUND, Response.EMPTY);
            }
            return Response.ok(response.toString());
        } catch (IOException e) {
            return new Response(Response.INTERNAL_ERROR, daoFactory.fromString(e.getMessage()).array());
        }
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(Request request, @Param(value = "id", required = true) String id) {
        try {
            if (id.isEmpty()) {
                return new Response(Response.BAD_REQUEST, Response.EMPTY);
            }
            ByteBuffer key = daoFactory.fromString(id);
            ByteBuffer value = ByteBuffer.wrap(request.getBody());
            if (daoFactory.toString(value).isEmpty()) {
                return new Response(Response.BAD_REQUEST, Response.EMPTY);
            }
            dao.upsert(new TypedBaseEntry(key, value));
            return new Response(Response.CREATED, Response.EMPTY);
        } catch (RuntimeException e) {
            return new Response(Response.INTERNAL_ERROR, daoFactory.fromString(e.getMessage()).array());
        }
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(@Param(value = "id", required = true) String id) {
        try {
            if (id.isEmpty()) {
                return new Response(Response.BAD_REQUEST, Response.EMPTY);
            }
            ByteBuffer key = daoFactory.fromString(id);
            dao.upsert(new TypedBaseEntry(key, null));
            return new Response(Response.CREATED, Response.EMPTY);
        } catch (RuntimeException e) {
            return new Response(Response.INTERNAL_ERROR, daoFactory.fromString(e.getMessage()).array());
        }
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
            try {
                return new MyService(config);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
