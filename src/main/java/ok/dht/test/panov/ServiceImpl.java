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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ServiceImpl implements Service {

    private static final long FLUSH_THRESHOLD_BYTES = 4 * 1024 * 1024;
    private static final long AWAIT_TIMEOUT_MILLISECONDS = 1000;
    private static final List<Integer> ALLOWED_METHODS =
            List.of(Request.METHOD_GET, Request.METHOD_PUT, Request.METHOD_DELETE);
    static final String JAVA_NET_DELEGATE_HEADER = "DELEGATE";
    static final String DELEGATE_HEADER = JAVA_NET_DELEGATE_HEADER + ':';
    static final String DEFAULT_ROOT = "/v0/entity";

    private final ServiceConfig config;
    private HttpServer server;
    private HttpClient client;
    private MemorySegmentDao dao;
    private NodeRouter router;

    public ServiceImpl(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
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

    @Path(DEFAULT_ROOT)
    public Response handleEntity(
            final Request request,
            @Param(value = "id", required = true) final String id,
            @Param(value = "from") final String from,
            @Param(value = "ack") final String ack
    ) {
        if (id == null || id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, "Id is empty".getBytes(StandardCharsets.UTF_8));
        }

        if (!ALLOWED_METHODS.contains(request.getMethod())) {
            return new Response(Response.METHOD_NOT_ALLOWED, "Unhandled method".getBytes(StandardCharsets.UTF_8));
        }

        ReplicasAcknowledgment acknowledgmentParams;

        try {
            acknowledgmentParams = new ReplicasAcknowledgment(ack, from, config.clusterUrls().size());
        } catch (IllegalAcknowledgmentArgumentsException e) {
            return new Response(Response.BAD_REQUEST, e.getMessage().getBytes(StandardCharsets.UTF_8));
        }

        String delegateValue = request.getHeader(DELEGATE_HEADER);
        if (delegateValue == null) {
            request.addHeader(DELEGATE_HEADER + System.currentTimeMillis());
            List<String> targetUrls = router.getUrls(id.getBytes(StandardCharsets.UTF_8), acknowledgmentParams);

            List<Response> responses = new ArrayList<>();
            for (final String targetUrl : targetUrls) {
                responses.add(handleRequest(request, id, targetUrl));
            }

            ResponseResolver responseResolver = new ResponseResolver(acknowledgmentParams);
            return responseResolver.resolve(responses);
        } else {
            return handleRequest(request, id, config.selfUrl());
        }

    }

    private Response handleRequest(final Request request, final String id, final String targetUrl) {
        String timeHeader = request.getHeader(DELEGATE_HEADER);
        long timestamp = Long.parseLong(timeHeader);

        if (targetUrl.equals(config.selfUrl())) {
            int requestMethod = request.getMethod();

            if (requestMethod == Request.METHOD_GET) {
                return getEntity(id);
            } else if (requestMethod == Request.METHOD_PUT) {
                return putEntity(request, id, timestamp);
            } else {
                return deleteEntity(id, timestamp);
            }
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

            try {
                HttpResponse<byte[]> response = client
                        .sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray())
                        .get(AWAIT_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);

                Response resp = new Response(String.valueOf(response.statusCode()), response.body());
                Optional<String> headerValue = response.headers().firstValue(JAVA_NET_DELEGATE_HEADER);
                headerValue.ifPresent(s -> resp.addHeader(DELEGATE_HEADER + s));

                return resp;
            } catch (TimeoutException | InterruptedException | ExecutionException e) {
                return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
            }
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

    @ServiceFactory(stage = 4, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
