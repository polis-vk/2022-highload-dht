package ok.dht.test.mikhaylov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.mikhaylov.chunk.ChunkedResponse;
import ok.dht.test.mikhaylov.internal.InternalHttpClient;
import ok.dht.test.mikhaylov.internal.JavaHttpClient;
import ok.dht.test.mikhaylov.internal.ReplicaRequirements;
import ok.dht.test.mikhaylov.internal.ShardResponseProcessor;
import ok.dht.test.mikhaylov.resolver.ConsistentHashingResolver;
import ok.dht.test.mikhaylov.resolver.ShardResolver;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class MyService implements Service {

    private final ServiceConfig config;

    private final List<String> sortedShardUrls;

    private final int selfShardIndex;

    private HttpServer server;

    private RocksDB db;

    private final ShardResolver shardResolver;

    private InternalHttpClient internalHttpClient;

    private ExecutorService shardProcessorExecutor;

    private static final int SHARD_PROCESSOR_THREADS = 2;

    private static final int MAX_AGGREGATOR_REQUESTS = 4096;

    private static final Logger logger = LoggerFactory.getLogger(MyService.class);

    private static final byte[] EMPTY_ID_RESPONSE_BODY = strToBytes("Empty id");

    private static final Set<Integer> ENTITY_ALLOWED_METHODS = Set.of(Request.METHOD_GET, Request.METHOD_PUT,
            Request.METHOD_DELETE);

    private static final String ENTITY_PATH = "/v0/entity";

    private static final String ENTITIES_PATH = "/v0/entities";

    private static final String ENTITY_INTERNAL_PATH = "/v0/internal/entity";

    private static final Pattern ENTITY_PATH_PATTERN = Pattern.compile(ENTITY_PATH);

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
        shardProcessorExecutor = new ThreadPoolExecutor(
                SHARD_PROCESSOR_THREADS, SHARD_PROCESSOR_THREADS,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingDeque<>(MAX_AGGREGATOR_REQUESTS)
        );
        internalHttpClient = new JavaHttpClient();
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
        internalHttpClient.close();
        if (server != null) {
            server.stop();
        }
        server = null;
        try {
            if (db != null) {
                db.closeE();
            }
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
        db = null;
        return CompletableFuture.completedFuture(null);
    }

    private static byte[] strToBytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    public void route(Request request, HttpSession session) throws IOException {
        String path = request.getPath();
        switch (path) {
            case ENTITY_PATH -> handleEntity(request, session);
            case ENTITIES_PATH -> handleEntities(request, session);
            case ENTITY_INTERNAL_PATH -> session.sendResponse(handleInternal(request));
            default -> session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
        }
    }

    private void handleEntity(Request request, HttpSession session) throws IOException {
        String id = request.getParameter("id=");
        if (id == null || id.isEmpty()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, EMPTY_ID_RESPONSE_BODY));
            return;
        }
        if (!ENTITY_ALLOWED_METHODS.contains(request.getMethod())) {
            session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
            return;
        }
        int shard = shardResolver.resolve(id);
        ReplicaRequirements replicaRequirements;
        try {
            replicaRequirements = new ReplicaRequirements(request, config.clusterUrls().size());
        } catch (IllegalArgumentException e) {
            logger.error("Could not parse replicas", e);
            session.sendResponse(new Response(Response.BAD_REQUEST, strToBytes(e.getMessage())));
            return;
        }
        if (request.getMethod() == Request.METHOD_PUT) {
            request = DatabaseUtilities.attachMeta(request);
        }
        var responseProcessor = new ShardResponseProcessor(session, replicaRequirements, request.getMethod());
        for (int i = 0; i < replicaRequirements.getFrom(); i++) {
            int shardIndex = (shard + i) % config.clusterUrls().size();
            if (shardIndex == selfShardIndex) {
                Request finalRequest = request;
                shardProcessorExecutor.execute(() -> {
                    try {
                        responseProcessor.process(handleLocalRequest(finalRequest, id));
                    } catch (IOException e) {
                        logger.error("Could not process local request", e);
                    }
                });
            } else {
                proxyRequest(request, shardIndex, responseProcessor);
            }
        }
    }

    private void handleEntities(Request request, HttpSession session) throws IOException {
        if (request.getMethod() != Request.METHOD_GET) {
            session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
            return;
        }
        String start = request.getParameter("start=");
        String end = request.getParameter("end=");
        if (start == null || start.isEmpty() || (end != null && end.isEmpty())
                || (end != null && start.compareTo(end) > 0)) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        session.sendResponse(new ChunkedResponse(new EntitiesIterator(db.newIterator(), start, end)));
    }

    // Assumes everything is valid
    public Response handleInternal(Request request) {
        // todo: verify that the request is not coming from outside
        String id = request.getParameter("id=");
        return handleLocalRequest(request, id);
    }

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

    private void proxyRequest(Request request, int shardIndex, ShardResponseProcessor responseProcessor) {
        String shard = sortedShardUrls.get(shardIndex);
        internalHttpClient.proxyRequest(request, shard)
                .whenCompleteAsync((response, throwable) -> {
                    try {
                        if (throwable != null) {
                            logger.error("Error while proxying request: {}", request, throwable);
                            responseProcessor.process(null);
                        } else {
                            responseProcessor.process(response);
                        }
                    } catch (IOException e) {
                        logger.error("Could not process response", e);
                    }
                }, shardProcessorExecutor);
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

    @ServiceFactory(stage = 6, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new MyService(config, new ConsistentHashingResolver(config.clusterUrls().size()));
        }
    }

    class MyHttpServer extends HttpServer {
        private final ExecutorService requestHandlers;

        private static final int REQUEST_HANDLERS = 4;
        private static final int MAX_REQUESTS = 128;

        public MyHttpServer(HttpServerConfig config) throws IOException {
            super(config);
            requestHandlers = new ThreadPoolExecutor(REQUEST_HANDLERS, REQUEST_HANDLERS, 0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingDeque<>(MAX_REQUESTS));
        }

        @Override
        public HttpSession createSession(Socket socket) {
            return new HttpSession(socket, this) {
                @Override
                protected void writeResponse(Response response, boolean writeBody) throws IOException {
                    if (response instanceof ChunkedResponse chunkedResponse) {
                        super.writeResponse(response, false);
                        chunkedResponse.writeBody(this);
                    } else {
                        super.writeResponse(response, writeBody);
                    }
                }
            };
        }

        @Override
        public void handleRequest(Request request, HttpSession session) throws IOException {
            try {
                requestHandlers.submit(() -> {
                    try {
                        route(request, session);
                    } catch (IOException e) {
                        logger.error("Could not send response to {}", session, e);
                    }
                });
            } catch (RejectedExecutionException ignored) {
                session.sendError(Response.SERVICE_UNAVAILABLE, "Server is overloaded");
            }
        }

        @Override
        public synchronized void stop() {
            requestHandlers.shutdown();
            try {
                if (!requestHandlers.awaitTermination(1, TimeUnit.SECONDS)) {
                    requestHandlers.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            for (SelectorThread selectorThread : selectors) {
                for (Session session : selectorThread.selector) {
                    session.close();
                }
            }
            super.stop();
        }
    }
}
