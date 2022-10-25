package ok.dht.test.shashulovskiy;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.shashulovskiy.hashing.MD5Hasher;
import ok.dht.test.shashulovskiy.metainfo.MetadataUtils;
import ok.dht.test.shashulovskiy.sharding.CircuitBreaker;
import ok.dht.test.shashulovskiy.sharding.ConsistentHashingShardingManager;
import ok.dht.test.shashulovskiy.sharding.ShardingManager;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import one.nio.util.Utf8;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.iq80.leveldb.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;

import static org.iq80.leveldb.impl.Iq80DBFactory.factory;

public class ServiceImpl implements Service {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceImpl.class);
    private static final int VNODES_COUNT = 5;
    private static final String INTERNAL_PREFIX = "/internal";

    private static final String NOT_ENOUGH_REPLICAS_RESPONSE = "504 Not Enough Replicas";

    private static final Duration REQUEST_TIMEOUT = Duration.of(2, ChronoUnit.SECONDS);
    private CircuitBreaker circuitBreaker;

    private final ServiceConfig config;
    private HttpServer server;
    private final HttpClient client = HttpClient.newHttpClient();

    private DB dao;

    private final ShardingManager shardingManager;

    private final int defaultAck;
    private final int defaultFrom;
    private final int totalShards;

    public ServiceImpl(ServiceConfig config) {
        this.config = config;
        this.shardingManager = new ConsistentHashingShardingManager(
                config.clusterUrls(),
                config.selfUrl(),
                VNODES_COUNT,
                new MD5Hasher()
        );
        this.defaultFrom = config.clusterUrls().size();
        this.defaultAck = defaultFrom / 2 + 1;
        this.totalShards = defaultFrom;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        Options options = new Options();
        options.createIfMissing(true);

        this.dao = factory.open(config.workingDir().toFile(), options);

        this.circuitBreaker = new CircuitBreaker(config.clusterUrls().size());

        server = new HttpServerImpl(createConfigFromPort(config.selfPort()), config.clusterUrls().size());
        server.addRequestHandlers(this);
        server.start();

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        dao.close();

        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    public void handle(Request request, HttpSession session) {
        try {
            String id = request.getParameter("id=");

            if (id == null) {
                session.sendResponse(new Response(
                        Response.BAD_REQUEST,
                        Utf8.toBytes("No id provided")
                ));
                return;
            } else if (id.isEmpty()) {
                session.sendResponse(new Response(
                        Response.BAD_REQUEST,
                        Utf8.toBytes("Empty id")
                ));
                return;
            }

            String ackString = request.getParameter("ack=");
            String fromString = request.getParameter("from=");

            int ack;
            int from;

            if (ackString == null) {
                ack = defaultAck;
            } else {
                try {
                    ack = Integer.parseInt(ackString);
                } catch (NumberFormatException e) {
                    LOG.error("Unable to parse ack", e);
                    session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                    return;
                }
            }

            if (fromString == null) {
                from = defaultFrom;
            } else {
                try {
                    from = Integer.parseInt(fromString);
                } catch (NumberFormatException e) {
                    LOG.error("Unable to parse from", e);
                    session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                    return;
                }
            }

            if (ack <= 0 || from < ack || from > config.clusterUrls().size()) {
                session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                return;
            }

            byte[] idBytes = Utf8.toBytes(id);
            byte[] body = request.getMethod() == Request.METHOD_PUT
                    ? MetadataUtils.wrapWithMetadata(request.getBody(), false) : new byte[0];

            int firstShard = shardingManager.getShard(idBytes).getNodeId();
            int acksCount = 0;

            Response[] responses = new Response[from];
            boolean responded = false;

            for (int shard = firstShard; shard < firstShard + from; ++shard) {
                Response response;
                if (config.selfUrl().equals(config.clusterUrls().get(shard % totalShards))) {
                    response = handleDbOperation(request, idBytes, body);
                } else {
                    response = handleProxyOperation(request, shard % totalShards, body);
                }
                responses[shard - firstShard] = response;
                if (response != null) {
                    acksCount++;
                }

                if (!responded && acksCount >= ack) {
                    onAcksCollected(request, session, responses);
                    responded = true;

                    if (request.getMethod() == Request.METHOD_GET) {
                        return;
                    }
                }
            }

            if (!responded) {
                session.sendResponse(new Response(NOT_ENOUGH_REPLICAS_RESPONSE, Response.EMPTY));
            }
        } catch (IOException e) {
            LOG.error("IOException occurred when sending response", e);
        }
    }

    private void onAcksCollected(Request request, HttpSession session, Response[] responses) throws IOException {
        switch (request.getMethod()) {
            case Request.METHOD_GET -> {
                int successCount = 0;
                long lastTimestamp = 0;
                byte[] lastResponse = new byte[0];

                for (var response : responses) {
                    if (response != null && response.getStatus() == 200) {
                        successCount++;
                        long currentResponseTimestamp = MetadataUtils.extractTimestamp(response.getBody());

                        if (currentResponseTimestamp > lastTimestamp) {
                            lastTimestamp = currentResponseTimestamp;
                            lastResponse = response.getBody();
                        }
                    }
                }

                if (successCount == 0 || MetadataUtils.isTombstone(lastResponse)) {
                    session.sendResponse(new Response(Response.NOT_FOUND, Response.EMPTY));
                } else {
                    session.sendResponse(new Response(Response.OK, MetadataUtils.extractData(lastResponse)));
                }
            }
            case Request.METHOD_PUT -> session.sendResponse(new Response(Response.CREATED, Response.EMPTY));
            case Request.METHOD_DELETE -> session.sendResponse(new Response(Response.ACCEPTED, Response.EMPTY));
            default -> session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
        }
    }

    private Response handleProxyOperation(Request request, int shardId, byte[] body) {
        try {
            if (circuitBreaker.isActive(shardId)) {
                HttpResponse<byte[]> response = client.send(
                        HttpRequest
                                .newBuilder()
                                .method(request.getMethodName(),
                                        HttpRequest.BodyPublishers.ofByteArray(request.getBody() == null
                                                ? Response.EMPTY : body))
                                .uri(URI.create(config.clusterUrls().get(shardId) + INTERNAL_PREFIX + request.getURI()))
                                .timeout(REQUEST_TIMEOUT).build(),
                        HttpResponse.BodyHandlers.ofByteArray()
                );

                circuitBreaker.successOn(shardId);
                return new Response(Integer.toString(response.statusCode()), response.body());
            } else {
                return null;
            }
        } catch (IOException | InterruptedException e) {
            circuitBreaker.failOn(shardId);
            return null;
        }
    }

    private Response handleDbOperation(Request request, byte[] idBytes, byte[] body) {
        try {
            switch (request.getMethod()) {
                case Request.METHOD_GET -> {
                    byte[] result = dao.get(idBytes);
                    if (result == null) {
                        return new Response(Response.NOT_FOUND, Response.EMPTY);
                    } else {
                        return new Response(Response.OK, result);
                    }
                }
                case Request.METHOD_PUT -> {
                    dao.put(idBytes, body);

                    return new Response(Response.CREATED, Response.EMPTY);
                }
                case Request.METHOD_DELETE -> {
                    dao.put(idBytes, MetadataUtils.wrapWithMetadata(new byte[0], true));

                    return new Response(Response.ACCEPTED, Response.EMPTY);
                }
                default -> {
                    return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
                }
            }
        } catch (DBException exception) {
            LOG.error("Internal dao exception occurred on " + request.getPath(), exception);
            return null;
        }
    }

    @Path(INTERNAL_PREFIX + "/v0/entity")
    public Response handleInternal(Request request) {
        // No additional validations on internal api
        String id = request.getParameter("id=");

        Response response = handleDbOperation(request, Utf8.toBytes(id), request.getBody());

        if (response == null) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        } else {
            return response;
        }
    }

    @Path("/stats/handledKeys")
    @RequestMethod(Request.METHOD_GET)
    public Response handleKeyStats() {
        return new Response(Response.OK, Utf8.toBytes(Long.toString(shardingManager.getHandledKeys())));
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @ServiceFactory(stage = 4, week = 1, bonuses = {"SingleNodeTest#respectFileFolder"})
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
