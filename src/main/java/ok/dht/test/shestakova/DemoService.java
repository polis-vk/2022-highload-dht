package ok.dht.test.shestakova;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.shestakova.dao.MemorySegmentDao;
import ok.dht.test.shestakova.dao.base.BaseEntry;
import ok.dht.test.shestakova.dao.base.Config;
import one.nio.http.HttpServerConfig;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static java.net.http.HttpClient.newHttpClient;

public class DemoService implements Service {

    private final ServiceConfig config;
    private DemoHttpServer server;
    private MemorySegmentDao dao;
    private static final long FLUSH_THRESHOLD = 1 << 20; // 1 MB
    private static final int POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final int QUEUE_CAPACITY = 256;
    private static final long KEEP_ALIVE_TIME = 0L;
    private ExecutorService workersPool;
    private HttpClient httpClient;

    public DemoService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        if (Files.notExists(config.workingDir())) {
            Files.createDirectory(config.workingDir());
        }

        dao = new MemorySegmentDao(new Config(config.workingDir(), FLUSH_THRESHOLD));
        workersPool = new ThreadPoolExecutor(
                POOL_SIZE,
                POOL_SIZE,
                KEEP_ALIVE_TIME,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY)
        );
        httpClient = newHttpClient();
        server = new DemoHttpServer(createConfigFromPort(config.selfPort()), httpClient, workersPool, config);
        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        workersPool.shutdownNow();
        dao.close();
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response handleGet(@Param(value = "id") String id) {
        BaseEntry<MemorySegment> entry = dao.get(fromString(id));
        if (entry == null) {
            return new Response(
                    Response.NOT_FOUND,
                    Response.EMPTY
            );
        }
        return new Response(
                Response.OK,
                entry.value().toByteArray()
        );
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(Request request, @Param(value = "id") String id) {
        dao.upsert(new BaseEntry<>(
                fromString(id),
                MemorySegment.ofArray(request.getBody())
        ));
        return new Response(
                Response.CREATED,
                Response.EMPTY
        );
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(@Param(value = "id") String id) {
        dao.upsert(new BaseEntry<>(
                fromString(id),
                null
        ));
        return new Response(
                Response.ACCEPTED,
                Response.EMPTY
        );
    }

    @Path("/service/message/ill")
    @RequestMethod(Request.METHOD_PUT)
    public void handleIllnessMessage(Request request) {
        server.putNodesIllnessInfo(Arrays.toString(request.getBody()), true);
    }

    @Path("/service/message/healthy")
    @RequestMethod(Request.METHOD_PUT)
    public void handleHealthMessage(Request request) {
        server.putNodesIllnessInfo(Arrays.toString(request.getBody()), false);
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    public MemorySegment fromString(String data) {
        return data == null ? null : MemorySegment.ofArray(data.getBytes(StandardCharsets.UTF_8));
    }

    @ServiceFactory(stage = 3, week = 1)
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new DemoService(config);
        }
    }
}
