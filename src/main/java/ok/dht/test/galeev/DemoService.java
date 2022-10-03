package ok.dht.test.galeev;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.galeev.dao.DaoMiddleLayer;
import ok.dht.test.galeev.dao.entry.Entry;
import ok.dht.test.galeev.dao.utils.DaoConfig;
import ok.dht.test.galeev.dao.utils.StringByteConverter;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DemoService implements Service {


    public static final int FLUSH_THRESHOLD_BYTES = 16777216; // 16MB
    protected SkipOldThreadExecutorFactory skipOldThreadExecutorFactory = new SkipOldThreadExecutorFactory();
    private final ServiceConfig config;
    private HttpServer server;
    private DaoMiddleLayer<String, byte[]> dao;
    private ThreadPoolExecutor executorService;

    public DemoService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        dao = getDao(config);
        executorService = skipOldThreadExecutorFactory.getExecutor();
        server = getServer(config.selfPort(), executorService);
        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        executorService.shutdown();
        dao.stop();
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response handleGet(String id)
            throws IOException {
        if (id == null || id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }

        Entry<String, byte[]> entry = dao.get(id);
        if (entry == null) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }
        return new Response(Response.OK, entry.value());
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(Request request, String id) {
        if (id == null || id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        dao.upsert(id, request.getBody());
        return new Response(Response.CREATED, Response.EMPTY);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(String id) {
        if (id == null || id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }
        dao.delete(id);
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    private static DaoMiddleLayer<String, byte[]> getDao(ServiceConfig config)
            throws IOException {
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

    private static CustomHttpServer getServer(final int port, ThreadPoolExecutor executorService) throws IOException {
        return new CustomHttpServer(createConfigFromPort(port), executorService);
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @ServiceFactory(stage = 2, week = 1, bonuses = {"SingleNodeTest#respectFileFolder"})
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new DemoService(config);
        }
    }
}
