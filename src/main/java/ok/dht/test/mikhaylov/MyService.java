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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class MyService implements Service {

    private final ServiceConfig config;

    private HttpServer server;

    private RocksDB db;

    private final ShardResolver shardResolver;

    private InternalHttpClient internalHttpClient;

    private static final Logger logger = LoggerFactory.getLogger(MyService.class);

    private static final byte[] EMPTY_ID_RESPONSE_BODY = strToBytes("Empty id");

    private static final Set<Integer> ALLOWED_METHODS = Set.of(Request.METHOD_GET, Request.METHOD_PUT,
            Request.METHOD_DELETE);

    public static final String ENTITY_PATH = "/v0/entity";

    public static final String ENTITY_INTERNAL_PATH = "/v0/internal/entity";

    public MyService(ServiceConfig config, ShardResolver shardResolver) {
        this.config = config;
        this.shardResolver = shardResolver;
    }

    public static Response makeError(Logger logger, String shard, Exception e) {
        logger.error("Could not proxy request to {}", shard, e);
        return new Response(Response.INTERNAL_ERROR, new byte[0]);
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        internalHttpClient = new OneNioHttpClient(config.clusterUrls());
        try {
            db = RocksDB.open(config.workingDir().toString());
        } catch (RocksDBException e) {
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
        String shard = shardResolver.resolve(id);
        if (shard.equals(config.selfUrl())) {
            shard = null;
        }
        return handleValidated(request, id, shard);
    }

    // Assumes everything is valid and the shard containing the id is this node
    @Path(ENTITY_INTERNAL_PATH)
    public Response handleInternal(Request request) {
        // todo: verify that the request is not coming from outside
        String id = request.getParameter("id=");
        return handleValidated(request, id, null);
    }

    // shard is null if the shard is this node
    private Response handleValidated(Request request, String id, @Nullable String shard) {
        if (shard != null) {
            return proxyRequest(request, shard);
        } else {
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
    }

    private Response proxyRequest(Request request, String shard) {
        try {
            return internalHttpClient.proxyRequest(request, shard);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return makeError(logger, shard, e);
        } catch (ExecutionException | TimeoutException e) {
            return makeError(logger, shard, e);
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
        db.delete(strToBytes(id));
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    @ServiceFactory(stage = 3, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new MyService(config, new ConsistentHashingResolver(config.clusterUrls()));
        }
    }

}
