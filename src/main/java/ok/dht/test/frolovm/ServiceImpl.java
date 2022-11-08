package ok.dht.test.frolovm;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.drozdov.dao.Config;
import ok.dht.test.drozdov.dao.Entry;
import ok.dht.test.drozdov.dao.MemorySegmentDao;
import ok.dht.test.frolovm.hasher.Hasher;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import one.nio.util.Hash;
import one.nio.util.Utf8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class ServiceImpl implements Service {

    private static final int FLUSH_THRESHOLD_BYTES = 1_048_576;
    private static final String PATH_ENTITY = "/v0/entity";
    private static final String PARAM_ID_NAME = "id=";
    private static final String BAD_ID = "Given id is bad.";
    private static final String NO_SUCH_METHOD = "No such method.";
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceImpl.class);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(1);
    private static final int MAX_REQUEST_TRIES = 100;
    private final ServiceConfig config;
    private final ShardingAlgorithm algorithm;
    private final HttpClient client = HttpClient.newHttpClient();
    private final CircuitBreakerImpl circuitBreaker;

    private MemorySegmentDao dao;
    private HttpServerImpl server;

    public ServiceImpl(ServiceConfig config) {
        this(config, Hash::murmur3);
    }

    public ServiceImpl(ServiceConfig config, Hasher hasher) {
        this.config = config;
        this.algorithm = new RendezvousHashing(config.clusterUrls(), hasher);
        this.circuitBreaker = new CircuitBreakerImpl(MAX_REQUEST_TRIES, config.clusterUrls());
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = createAcceptorConfig(port);
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    private static AcceptorConfig createAcceptorConfig(int port) {
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        return acceptor;
    }

    private void createDao() throws IOException {
        this.dao = new MemorySegmentDao(new Config(config.workingDir(), FLUSH_THRESHOLD_BYTES));
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        if (dao == null) {
            createDao();
        }

        server = new HttpServerImpl(createConfigFromPort(config.selfPort()));
        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    private Response getHandler(String id) {
        Entry result = dao.get(Utils.stringToSegment(id));
        if (result == null) {
            return Utils.emptyResponse(Response.NOT_FOUND);
        } else {
            return new Response(Response.OK, result.value().toByteArray());
        }
    }

    @Path(PATH_ENTITY)
    public Response entityHandler(@Param(PARAM_ID_NAME) String id, Request request, HttpSession session) {
        if (!Utils.checkId(id)) {
            return new Response(Response.BAD_REQUEST, Utf8.toBytes(BAD_ID));
        }
        switch (request.getMethod()) {
            case Request.METHOD_PUT:
            case Request.METHOD_GET:
            case Request.METHOD_DELETE:
                Shard shard = algorithm.chooseShard(id);
                if (shard.getName().equals(config.selfUrl())) {
                    return entityHandlerSelf(id, request);
                } else {
                    if (circuitBreaker.isReady(shard.getName())) {
                        try {
                            return sendResponseToAnotherNode(request, shard);
                        } catch (IOException | InterruptedException e) {
                            LOGGER.error("Something bad happens when client answer", e);
                            circuitBreaker.incrementFail(shard.getName());
                            return Utils.emptyResponse(Response.SERVICE_UNAVAILABLE);
                        }
                    } else {
                        LOGGER.info("Node is unavailable right now");
                        return Utils.emptyResponse(Response.SERVICE_UNAVAILABLE);
                    }
                }
            default:
                LOGGER.info("Method is not allowed: " + request.getMethod());
                return new Response(Response.METHOD_NOT_ALLOWED, Utf8.toBytes(NO_SUCH_METHOD));
        }
    }

    private Response sendResponseToAnotherNode(Request request, Shard shard) throws IOException, InterruptedException {
        byte[] body = request.getBody() == null ? Response.EMPTY : request.getBody();
        HttpResponse<byte[]> response =
                client.send(HttpRequest.newBuilder().uri(URI.create(shard.getName() + request.getURI())).method(
                                request.getMethodName(),
                                HttpRequest.BodyPublishers.ofByteArray(body)).timeout(RESPONSE_TIMEOUT).build(),
                        HttpResponse.BodyHandlers.ofByteArray());
        if (Utils.isServerError(response)) {
            circuitBreaker.incrementFail(shard.getName());
        } else {
            circuitBreaker.successRequest(shard.getName());
        }
        String responseStatus = Utils.STATUS_MAP.get(response.statusCode());
        byte[] answer = response.body();
        if (responseStatus == null) {
            throw new IllegalArgumentException("Unknown status code: " + response.statusCode());
        }
        return new Response(responseStatus, answer);
    }

    private Response entityHandlerSelf(String id, Request request) {
        if (!Utils.checkId(id)) {
            return new Response(Response.BAD_REQUEST, Utf8.toBytes(BAD_ID));
        }
        switch (request.getMethod()) {
            case Request.METHOD_PUT:
                return putHandler(request, id);
            case Request.METHOD_GET:
                return getHandler(id);
            case Request.METHOD_DELETE:
                return deleteHandler(id);
            default:
                return new Response(Response.METHOD_NOT_ALLOWED, Utf8.toBytes(NO_SUCH_METHOD));
        }
    }

    private Response putHandler(Request request, String id) {
        MemorySegment bodySegment = MemorySegment.ofArray(request.getBody());
        dao.upsert(new Entry(Utils.stringToSegment(id), bodySegment));
        return Utils.emptyResponse(Response.CREATED);
    }

    private void closeExecutorPool(ExecutorService pool) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                if (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
                    LOGGER.error("Pool didn't terminate");
                }
            }
        } catch (InterruptedException ie) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private Response deleteHandler(String id) {
        dao.upsert(new Entry(Utils.stringToSegment(id), null));
        return Utils.emptyResponse(Response.ACCEPTED);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        if (server != null) {
            server.close();
            server = null;
        }
        if (dao != null) {
            dao.close();
        }
        dao = null;
        return CompletableFuture.completedFuture(null);
    }

    @ServiceFactory(stage = 3, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
