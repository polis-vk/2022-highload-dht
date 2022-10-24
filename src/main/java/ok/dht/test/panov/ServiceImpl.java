package ok.dht.test.panov;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.panov.dao.BaseEntry;
import ok.dht.test.panov.dao.Config;
import ok.dht.test.panov.dao.Entry;
import ok.dht.test.panov.dao.lsm.MemorySegmentDao;
import ok.dht.test.panov.hash.NodeRouter;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ServiceImpl implements Service {

    private static final long FLUSH_THRESHOLD_BYTES = 4 * 1024 * 1024;
    private static final long AWAIT_TIMEOUT_SECONDS = 1;
    private static final long HEALTHCHECK_TIMEOUT_SECONDS = 5;

    private final ServiceConfig config;
    private HttpServer server;
    private HttpClient client;
    private MemorySegmentDao dao;
    private NodeRouter router;
    private Map<String, Boolean> isAlive;
    private ScheduledExecutorService turnAliveService;

    public ServiceImpl(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        isAlive = new HashMap<>();
        for (String url : config.clusterUrls()) {
            isAlive.put(url, true);
        }

        turnAliveService = Executors.newScheduledThreadPool(config.clusterUrls().size());

        server = new ConcurrentHttpServer(
                createConfigFromPort(config.selfPort()),
                new RoutingConfig(config.selfUrl(), config.clusterUrls())
        );
        server.addRequestHandlers(this);
        dao = new MemorySegmentDao(new Config(config.workingDir(), FLUSH_THRESHOLD_BYTES));
        router = new NodeRouter(config.clusterUrls());
        client = HttpClient.newHttpClient();
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        dao.close();

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
    public Response handleEntity(
            final Request request,
            @Param(value = "id", required = true) final String id,
            @Param(value = "from") final String from,
            @Param(value = "ack") final String ack
    ) {
        if (id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, "Id is empty".getBytes(StandardCharsets.UTF_8));
        }

        String targetUrl = router.getUrl(id.getBytes(StandardCharsets.UTF_8));
        if (targetUrl.equals(config.selfUrl())) {
            return switch (request.getMethod()) {
                case Request.METHOD_GET -> getEntity(id);
                case Request.METHOD_PUT -> putEntity(request, id);
                case Request.METHOD_DELETE -> deleteEntity(id);
                default ->
                        new Response(Response.METHOD_NOT_ALLOWED, "Unhandled method".getBytes(StandardCharsets.UTF_8));
            };
        } else {
            if (isAlive.get(targetUrl)) {
                byte[] requestBody = request.getBody();
                if (requestBody == null) requestBody = new byte[]{};
                HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(requestBody);

                HttpRequest httpRequest = HttpRequest
                        .newBuilder()
                        .method(request.getMethodName(), bodyPublisher)
                        .uri(URI.create(targetUrl + "/v0/entity?id=" + id))
                        .build();

                try {
                    HttpResponse<byte[]> response = client
                            .sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray())
                            .get(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                    return new Response(String.valueOf(response.statusCode()), response.body());
                } catch (TimeoutException | InterruptedException | ExecutionException e) {
                    isAlive.put(targetUrl, false);
                    turnAliveService.schedule(() -> {
                       isAlive.put(targetUrl, true);
                    }, HEALTHCHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
                }
            } else {
                return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
            }
        }
    }

    private Response getEntity(final String id) {
        MemorySegment key = MemorySegment.ofArray(id.getBytes(StandardCharsets.UTF_8));
        Entry<MemorySegment> value = dao.get(key);

        if (value == null) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }
        return new Response(Response.OK, value.value().toByteArray());
    }

    public Response putEntity(final Request request, final String id) {
        MemorySegment key = MemorySegment.ofByteBuffer(ByteBuffer.wrap(id.getBytes(StandardCharsets.UTF_8)));
        MemorySegment value = MemorySegment.ofByteBuffer(ByteBuffer.wrap(request.getBody()));

        Entry<MemorySegment> entry = new BaseEntry<>(key, value);
        dao.upsert(entry);

        return new Response(Response.CREATED, Response.EMPTY);
    }

    public Response deleteEntity(final String id) {
        MemorySegment key = MemorySegment.ofByteBuffer(ByteBuffer.wrap(id.getBytes(StandardCharsets.UTF_8)));

        Entry<MemorySegment> entry = new BaseEntry<>(key, null);
        dao.upsert(entry);

        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    @ServiceFactory(stage = 4, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
