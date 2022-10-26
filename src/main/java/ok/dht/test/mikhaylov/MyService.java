package ok.dht.test.mikhaylov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.mikhaylov.internal.InternalHttpClient;
import ok.dht.test.mikhaylov.internal.JavaHttpClient;
import ok.dht.test.mikhaylov.internal.OneNioHttpClient;
import ok.dht.test.mikhaylov.resolver.ConsistentHashingResolver;
import ok.dht.test.mikhaylov.resolver.ShardResolver;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

public class MyService implements Service {

    private final ServiceConfig config;

    private final List<String> sortedShardUrls;

    private final int selfShardIndex;

    private HttpServer server;

    private RocksDB db;

    private final ShardResolver shardResolver;

    private InternalHttpClient internalHttpClient;

    private static final Logger logger = LoggerFactory.getLogger(MyService.class);

    private static final byte[] EMPTY_ID_RESPONSE_BODY = strToBytes("Empty id");

    private static final Set<Integer> ALLOWED_METHODS = Set.of(Request.METHOD_GET, Request.METHOD_PUT,
            Request.METHOD_DELETE);

    private static final String ENTITY_PATH = "/v0/entity";

    private static final String ENTITY_INTERNAL_PATH = "/v0/internal/entity";

    private static final Pattern ENTITY_PATH_PATTERN = Pattern.compile(ENTITY_PATH);

    private static final String NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";

    public MyService(ServiceConfig config, ShardResolver shardResolver) {
        this.config = config;
        this.sortedShardUrls = config.clusterUrls().stream().sorted().toList();
        this.selfShardIndex = Collections.binarySearch(sortedShardUrls, config.selfUrl());
        this.shardResolver = shardResolver;
    }

    public static String convertPathToInternal(String path) {
        return ENTITY_PATH_PATTERN.matcher(path).replaceFirst(ENTITY_INTERNAL_PATH);
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        internalHttpClient = new OneNioHttpClient(config.clusterUrls());
        try {
            db = RocksDB.open(config.workingDir().toString());
        } catch (RocksDBException e) {
            logger.error("Could not open RocksDB", e);
            throw new IOException(e);
        }
        server = new MyHttpServer(createServerConfigFromPort(config.selfPort()));
        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    private static HttpServerConfig createServerConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        if (server != null) {
            server.stop();
        }
        server = null;
        try {
            if (db != null) {
                db.closeE();
            }
        } catch (RocksDBException e) {
            logger.error("Error while closing db", e);
            throw new IOException(e);
        }
        db = null;
        return CompletableFuture.completedFuture(null);
    }

    private static byte[] strToBytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Path(ENTITY_PATH)
    public Response handle(Request request) {
        String id = request.getParameter("id=");
        if (id == null || id.isEmpty()) {
            return new Response(Response.BAD_REQUEST, EMPTY_ID_RESPONSE_BODY);
        }
        if (!ALLOWED_METHODS.contains(request.getMethod())) {
            return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
        }
        int shard = shardResolver.resolve(id);
        Replicas replicas;
        try {
            replicas = new Replicas(request, config.clusterUrls().size());
        } catch (IllegalArgumentException e) {
            logger.error("Could not parse replicas", e);
            return new Response(Response.BAD_REQUEST, strToBytes(e.getMessage()));
        }
        if (request.getMethod() == Request.METHOD_PUT) {
            request = DatabaseUtilities.attachMeta(request);
        }
        List<Response> shardResponses = new ArrayList<>();
        for (int i = 0; i < replicas.getFrom(); i++) {
            int shardIndex = (shard + i) % config.clusterUrls().size();
            if (shardIndex == selfShardIndex) {
                shardResponses.add(handleLocalRequest(request, id));
            } else {
                Response response = proxyRequest(request, shardIndex);
                if (response != null) {
                    shardResponses.add(response);
                }
            }
        }
        return processShardResponses(request.getMethod(), replicas, shardResponses);
    }

    private static Response processShardResponses(int requestMethod, Replicas replicas, List<Response> shardResponses) {
        return switch (requestMethod) {
            case Request.METHOD_PUT -> {
                int acks = 0;
                for (Response response : shardResponses) {
                    if (JavaHttpClient.responseCodeToStatusText(response.getStatus()).equals(Response.CREATED)) {
                        acks++;
                    }
                }
                yield acks >= replicas.getAck() ? new Response(Response.CREATED, Response.EMPTY) :
                        new Response(NOT_ENOUGH_REPLICAS, Response.EMPTY);
            }
            case Request.METHOD_GET -> {
                int respondedCount = shardResponses.size();
                byte[] latestBody = null;
                long latestTimestamp = 0;
                boolean allNotFound = true;
                for (Response response : shardResponses) {
                    String statusText = JavaHttpClient.responseCodeToStatusText(response.getStatus());
                    if (!statusText.equals(Response.NOT_FOUND)) {
                        allNotFound = false;
                    }
                    if (statusText.equals(Response.OK)) {
                        byte[] body = response.getBody();
                        long timestamp = DatabaseUtilities.getTimestamp(body);
                        if (timestamp > latestTimestamp) {
                            latestBody = body;
                            latestTimestamp = timestamp;
                        }
                    }
                }
                if (respondedCount < replicas.getAck()) {
                    yield new Response(NOT_ENOUGH_REPLICAS, Response.EMPTY);
                } else if (allNotFound || latestBody == null || DatabaseUtilities.isTombstone(latestBody)) {
                    yield new Response(Response.NOT_FOUND, Response.EMPTY);
                } else {
                    byte[] value = DatabaseUtilities.getValue(latestBody);
                    yield new Response(Response.OK, value);
                }
            }
            case Request.METHOD_DELETE -> {
                int acks = 0;
                for (Response response : shardResponses) {
                    if (JavaHttpClient.responseCodeToStatusText(response.getStatus()).equals(Response.ACCEPTED)) {
                        acks++;
                    }
                }
                yield acks >= replicas.getAck() ? new Response(Response.ACCEPTED, Response.EMPTY) :
                        new Response(NOT_ENOUGH_REPLICAS, Response.EMPTY);
            }
            // Should never happen
            default -> {
                logger.error("Unexpected method: {}", requestMethod);
                throw new IllegalArgumentException("Method " + requestMethod + " is not allowed");
            }
        };
    }

    // Assumes everything is valid and the shard containing the id is this node
    @Path(ENTITY_INTERNAL_PATH)
    public Response handleInternal(Request request) {
        // todo: verify that the request is not coming from outside
        String id = request.getParameter("id=");
        return handleLocalRequest(request, id);
    }

    // shard is null if the shard is this node
    private Response handleLocalRequest(Request request, String id) {
        try {
            return switch (request.getMethod()) {
                case Request.METHOD_GET -> dbGet(id);
                case Request.METHOD_PUT -> dbPut(id, request.getBody());
                case Request.METHOD_DELETE -> dbDelete(id);
                // Should never happen
                default -> {
                    logger.error("Unexpected method: {}", request.getMethod());
                    throw new IllegalArgumentException("Method " + request.getMethod() + " is not allowed");
                }
            };
        } catch (RocksDBException e) {
            logger.error("RocksDB error while handling request: {}", request, e);
            return new Response(Response.INTERNAL_ERROR, strToBytes("Could not access database"));
        }
    }

    @Nullable
    private Response proxyRequest(Request request, int shardIndex) {
        String shard = sortedShardUrls.get(shardIndex);
        try {
            return internalHttpClient.proxyRequest(request, shard);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while proxying request to shard {}", shard, e);
            return null;
        } catch (ExecutionException | TimeoutException e) {
            logger.error("Could not proxy request to shard {}", shard, e);
            return null;
        }
    }

    private Response dbGet(final String id) throws RocksDBException {
        byte[] value = db.get(strToBytes(id));
        if (value == null) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        } else {
            return new Response(Response.OK, value);
        }
    }

    private Response dbPut(final String id, final byte[] body) throws RocksDBException {
        db.put(strToBytes(id), body);
        return new Response(Response.CREATED, Response.EMPTY);
    }

    private Response dbDelete(final String id) throws RocksDBException {
        byte[] idBytes = strToBytes(id);
        byte[] value = db.get(idBytes);
        if (value == null) {
            return new Response(Response.ACCEPTED, Response.EMPTY);
        }
        db.put(idBytes, DatabaseUtilities.markAsTombstone(value));
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    @ServiceFactory(stage = 4, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new MyService(config, new ConsistentHashingResolver(config.clusterUrls().size()));
        }
    }

    private static class Replicas {
        private final int ack;

        private final int from;

        public Replicas(Request request, int clusterSize) {
            String ackStr = request.getParameter("ack=");
            String fromStr = request.getParameter("from=");
            if (ackStr == null && fromStr == null) {
                ack = clusterSize / 2 + 1;
                from = clusterSize;
            } else if (ackStr == null || fromStr == null) {
                throw new IllegalArgumentException("Both ack and from must be specified (or neither)");
            } else {
                try {
                    ack = Integer.parseInt(ackStr);
                    from = Integer.parseInt(fromStr);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Could not parse ack or from", e);
                }
                if (ack <= 0) {
                    throw new IllegalArgumentException("Ack must be positive");
                }
                if (from <= 0) {
                    throw new IllegalArgumentException("From must be positive");
                }
                if (from > clusterSize) {
                    throw new IllegalArgumentException("From must be less than cluster size");
                }
                if (ack > from) {
                    throw new IllegalArgumentException("Ack must be less than or equal to from");
                }
            }
        }

        public int getAck() {
            return ack;
        }

        public int getFrom() {
            return from;
        }
    }

}
