package ok.dht.test.kovalenko;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.kovalenko.dao.LSMDao;
import ok.dht.kovalenko.dao.aliases.TypedBaseEntry;
import ok.dht.kovalenko.dao.aliases.TypedEntry;
import ok.dht.kovalenko.dao.base.ByteBufferDaoFactory;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public class MyService implements Service {

    private static final ByteBufferDaoFactory daoFactory = new ByteBufferDaoFactory();
    private final ServiceConfig config;
    private LSMDao dao;
    private HttpServer server;

    public MyService(ServiceConfig config) throws IOException {
        this.config = config;
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        try {
            this.dao = new LSMDao(this.config);
            this.server = new MyServer(createConfigFromPort(this.config.selfPort()));
            this.server.addRequestHandlers(this);
            this.server.start();
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        this.dao.close();
        this.server.stop();
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response handleGet(@Param(value = "id") String id) throws IOException {
        if (id == null || id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        ByteBuffer key = daoFactory.fromString(id);
        TypedEntry res = this.dao.get(key);
        if (res == null) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }
        return Response.ok(res.value().array());
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(Request request, @Param(value = "id") String id) {
        if (id == null || id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        ByteBuffer key = daoFactory.fromString(id);
        ByteBuffer value = ByteBuffer.wrap(request.getBody());
        this.dao.upsert(new TypedBaseEntry(key, value));
        return new Response(Response.CREATED, Response.EMPTY);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(@Param(value = "id") String id) {
        if (id == null || id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        ByteBuffer key = daoFactory.fromString(id);
        this.dao.upsert(new TypedBaseEntry(key, null));
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }
}
