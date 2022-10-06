package ok.dht.test.shik;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import one.nio.http.HttpServerConfig;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.net.ConnectionString;
import one.nio.server.ServerConfig;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.Iq80DBFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public class ServiceImpl implements Service {

    private static final Log LOG = LogFactory.getLog(ServiceImpl.class);

    private final ServiceConfig config;
    private CustomHttpServer server;
    private DB levelDB;

    public ServiceImpl(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        try {
            levelDB = Iq80DBFactory.factory.open(config.workingDir().toFile(), new Options());
        } catch (IOException e) {
            LOG.error("Error while starting database: ", e);
            throw e;
        }
        server = new CustomHttpServer(createHttpConfig(config));
        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() {
        server.stop();
        try {
            levelDB.close();
        } catch (IOException e) {
            LOG.error("Error while closing: ", e);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response handleGet(@Param(value = "id", required = true) String id) {
        if (notValidId(id)) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        byte[] value = levelDB.get(id.getBytes(StandardCharsets.UTF_8));
        return value == null ? new Response(Response.NOT_FOUND, Response.EMPTY) : Response.ok(value);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(@Param(value = "id", required = true) String id, Request request) {
        if (notValidId(id) || !isValidBody(request)) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        byte[] value = request.getBody();
        levelDB.put(id.getBytes(StandardCharsets.UTF_8), value);
        return new Response(Response.CREATED, Response.EMPTY);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(@Param(required = true, value = "id") String id) {
        if (notValidId(id)) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        levelDB.delete(id.getBytes(StandardCharsets.UTF_8));
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    private static boolean notValidId(String id) {
        return id == null || id.isEmpty();
    }

    private static boolean isValidBody(Request request) {
        return request.getBody() != null;
    }

    private static HttpServerConfig createHttpConfig(ServiceConfig config) {
        ServerConfig serverConfig = ServerConfig.from(new ConnectionString(config.selfUrl()));
        HttpServerConfig httpConfig = new HttpServerConfig();
        httpConfig.acceptors = serverConfig.acceptors;
        Arrays.stream(httpConfig.acceptors).forEach(x -> x.reusePort = true);
        httpConfig.schedulingPolicy = serverConfig.schedulingPolicy;
        return httpConfig;
    }

    @ServiceFactory(stage = 1, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
