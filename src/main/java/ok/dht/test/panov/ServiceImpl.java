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
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ServiceImpl implements Service {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceImpl.class);

    private static final long FLUSH_THRESHOLD_BYTES = 4 * 1024 * 1024;
    private static final long AWAIT_TIMEOUT_MILLISECONDS = 1000;
    private static final List<Integer> ALLOWED_METHODS =
            List.of(Request.METHOD_GET, Request.METHOD_PUT, Request.METHOD_DELETE);
    static final String JAVA_NET_DELEGATE_HEADER = "DELEGATE";
    static final String DELEGATE_HEADER = JAVA_NET_DELEGATE_HEADER + ':';
    static final String DEFAULT_ROOT = "/v0/entity";

    private static final int CORE_POOL_SIZE = 4;
    private static final int MAXIMUM_POOL_SIZE = 8;
    private static final long KEEP_ALIVE_TIME = 0;

    private final ServiceConfig config;
    private HttpServer server;
    private HttpClient client;
    private MemorySegmentDao dao;
    private NodeRouter router;
    private ExecutorService executorService;
    private RangeRequestHandler rangeRequestHandler;

    public ServiceImpl(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        executorService = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAXIMUM_POOL_SIZE,
                KEEP_ALIVE_TIME,
                TimeUnit.MICROSECONDS,
                new ArrayBlockingQueue<>(1024)
        );
        server = new ConcurrentHttpServer(
                createConfigFromPort(config.selfPort()),
                executorService,
                new RoutingConfig(config.selfUrl(), config.clusterUrls())
        );
        server.addRequestHandlers(this);
        dao = new MemorySegmentDao(new Config(config.workingDir(), FLUSH_THRESHOLD_BYTES));
        router = new NodeRouter(config.clusterUrls());
        client = HttpClient.newHttpClient();
        rangeRequestHandler = new RangeRequestHandler(dao, executorService);
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

    @Path(DEFAULT_ROOT)
    public void handleEntity(
            final Request request,
            final HttpSession session,
            @Param(value = "id") final String id,
            @Param(value = "from") final String from,
            @Param(value = "ack") final String ack
    ) {
        try {
            if (id == null || id.isEmpty()) {
                session.sendResponse(
                        new Response(Response.BAD_REQUEST, "Id is empty".getBytes(StandardCharsets.UTF_8)));
                return;
            }

            if (!ALLOWED_METHODS.contains(request.getMethod())) {
                session.sendResponse(
                        new Response(Response.METHOD_NOT_ALLOWED, "Unhandled method".getBytes(StandardCharsets.UTF_8)));
                return;
            }

            ReplicasAcknowledgment acknowledgmentParams = validateAcknowledgmentParams(ack, from, session);

            if (acknowledgmentParams == null) {
                return;
            }

            String delegateValue = request.getHeader(DELEGATE_HEADER);
            if (delegateValue == null) {
                request.addHeader(DELEGATE_HEADER + System.currentTimeMillis());
                List<String> targetUrls = router.getUrls(id.getBytes(StandardCharsets.UTF_8), acknowledgmentParams);

                ResponseResolver responseResolver = new ResponseResolver(acknowledgmentParams, executorService);
                for (final String targetUrl : targetUrls) {
                    responseResolver.add(handleRequest(request, id, targetUrl), session);
                }
            } else {
                handleLocalRequest(request, session, id);
            }
        } catch (IOException e) {
            LOGGER.error("Error during response sending");
        }

    }

    @Path("/v0/entities")
    public void handleEntities(
        final HttpSession session,
        @Param(value = "start") final String start,
        @Param(value = "end") final String end) {
        try {
            if (start == null || start.isEmpty()) {
                session.sendResponse(
                        new Response(Response.BAD_REQUEST, "Start is empty".getBytes(StandardCharsets.UTF_8)));
                return;
            }

            rangeRequestHandler.handleLocalRangeRequest(session, start, end);
        } catch (IOException e) {
            LOGGER.error("Error during response sending");
        }
    }

    private void handleLocalRequest(Request request, HttpSession session, String id) throws IOException {
        try {
            session.sendResponse(
                    handleRequest(request, id, config.selfUrl())
                            .get(AWAIT_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
            );
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            session.sendResponse(new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY));
        }
    }

    private ReplicasAcknowledgment validateAcknowledgmentParams(
            String ack,
            String from,
            HttpSession session
    ) throws IOException {
        try {
            return new ReplicasAcknowledgment(ack, from, config.clusterUrls().size());
        } catch (IllegalAcknowledgmentArgumentsException e) {
            session.sendResponse(
                    new Response(Response.BAD_REQUEST, e.getMessage().getBytes(StandardCharsets.UTF_8)));
            return null;
        }
    }

    private CompletableFuture<Response> handleRequest(final Request request, final String id, final String targetUrl) {
        String timeHeader = request.getHeader(DELEGATE_HEADER);
        long timestamp = Long.parseLong(timeHeader);

        if (targetUrl.equals(config.selfUrl())) {
            int requestMethod = request.getMethod();

            CompletableFuture<Response> response;
            if (requestMethod == Request.METHOD_GET) {
                response = CompletableFuture.supplyAsync(() -> getEntity(id));
            } else if (requestMethod == Request.METHOD_PUT) {
                response = CompletableFuture.supplyAsync(() -> putEntity(request, id, timestamp));
            } else {
                response = CompletableFuture.supplyAsync(() -> deleteEntity(id, timestamp));
            }
            return response;
        } else {
            byte[] requestBody = request.getBody();
            if (requestBody == null) requestBody = new byte[]{};
            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(requestBody);

            HttpRequest httpRequest = HttpRequest
                    .newBuilder()
                    .method(request.getMethodName(), bodyPublisher)
                    .header(JAVA_NET_DELEGATE_HEADER, Long.toString(timestamp))
                    .uri(URI.create(targetUrl + DEFAULT_ROOT + "?id=" + id))
                    .build();

            CompletableFuture<HttpResponse<byte[]>> response = client
                    .sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray());

            return response.thenApply(httpResponse -> {
                Response resp = new Response(String.valueOf(httpResponse.statusCode()), httpResponse.body());
                Optional<String> headerValue = httpResponse.headers().firstValue(JAVA_NET_DELEGATE_HEADER);
                headerValue.ifPresent(s -> resp.addHeader(DELEGATE_HEADER + s));

                return resp;
            });
        }
    }

    private Response getEntity(final String id) {
        MemorySegment key = MemorySegment.ofArray(id.getBytes(StandardCharsets.UTF_8));
        Entry<MemorySegment> value = dao.get(key);

        Response resp;

        if (value == null) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        } else if (value.value() == null) {
            resp = new Response(Response.NOT_FOUND, Response.EMPTY);
        } else {
            resp = new Response(Response.OK, value.value().toByteArray());
        }

        resp.addHeader(DELEGATE_HEADER + value.timestamp());

        return resp;
    }

    public Response putEntity(final Request request, final String id, long timestamp) {
        MemorySegment key = MemorySegment.ofByteBuffer(ByteBuffer.wrap(id.getBytes(StandardCharsets.UTF_8)));
        MemorySegment value = MemorySegment.ofByteBuffer(ByteBuffer.wrap(request.getBody()));

        Entry<MemorySegment> entry = new BaseEntry<>(key, value, timestamp);
        dao.upsert(entry);

        return new Response(Response.CREATED, Response.EMPTY);
    }

    public Response deleteEntity(final String id, long timestamp) {
        MemorySegment key = MemorySegment.ofByteBuffer(ByteBuffer.wrap(id.getBytes(StandardCharsets.UTF_8)));

        Entry<MemorySegment> entry = new BaseEntry<>(key, null, timestamp);
        dao.upsert(entry);

        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    @ServiceFactory(stage = 6, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
