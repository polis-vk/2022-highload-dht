package ok.dht.test.ushkov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.ushkov.exception.BadPathException;
import ok.dht.test.ushkov.exception.InternalErrorException;
import ok.dht.test.ushkov.exception.InvalidParamsException;
import ok.dht.test.ushkov.exception.MethodNotAllowedException;
import ok.dht.test.ushkov.hashing.ConsistentHashing;
import ok.dht.test.ushkov.hashing.KeyManager;
import ok.dht.test.ushkov.replicating.ReplicatingRequestsAggregator;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import one.nio.util.Utf8;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class RocksDBService implements Service {
    public static final int N_SELECTOR_THREADS = 3;
    public static final int N_WORKER_THREADS = 3;
    public static final int N_WORKER1_THREADS = 2;
    public static final int EXECUTOR_QUEUE_CAPACITY = 2000;
    public static final int EXECUTOR1_QUEUE_CAPACITY = 2000;
    public static final int EXECUTOR_AWAIT_SHUTDOWN_TIMEOUT_MINUTES = 1;
    public static final int EXECUTOR_AWAIT1_SHUTDOWN_TIMEOUT_MINUTES = 1;
    public static final int NODE_QUEUE_TASKS_LIMIT = 128;
    public static final int NODE_QUEUE_TASKS_ON_EXECUTOR_LIMIT = 3;

    private static final String V0_ENTITY = "/v0/entity";
    private static final Logger LOG = LoggerFactory.getLogger(RocksDBService.class);

    private final ServiceConfig config;
    private final KeyManager keyManager = new ConsistentHashing();

    public RocksDB db;
    private HttpServer httpServer;

    private ExecutorService executor;
    private ExecutorService executor1;
    private HttpClient client;

    public RocksDBService(ServiceConfig config) {
        this.config = config;

        for (String url : config.clusterUrls()) {
            keyManager.addNode(url);
        }
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        LOG.debug("start node {}", config.selfUrl());

        executor = new ThreadPoolExecutor(
                N_WORKER_THREADS,
                N_WORKER_THREADS,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(EXECUTOR_QUEUE_CAPACITY)
        );

        executor1 = new ThreadPoolExecutor(
                N_WORKER1_THREADS,
                N_WORKER1_THREADS,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(EXECUTOR1_QUEUE_CAPACITY)
        );

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(500))
                .version(HttpClient.Version.HTTP_1_1)
                .executor(executor)
                .build();

        try {
            db = RocksDB.open(config.workingDir().toString());
        } catch (RocksDBException e) {
            throw new IOException(e);
        }

        HttpServerConfig httpServerConfig =
                createHttpServerConfigFromPort(config.selfPort());
        httpServer = createHttpServer(httpServerConfig);
        httpServer.start();

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        if (httpServer == null && db == null
                && executor == null && executor1 == null) {
            return CompletableFuture.completedFuture(null);
        }

        LOG.debug("stop node {}", config.selfUrl());

        httpServer.stop();
        httpServer = null;

        try {
            db.closeE();
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
        db = null;

        executor.shutdown();
        executor1.shutdown();

        return CompletableFuture.runAsync(() -> {
            try {
                executor.awaitTermination(EXECUTOR_AWAIT_SHUTDOWN_TIMEOUT_MINUTES, TimeUnit.MINUTES);
                executor1.awaitTermination(EXECUTOR_AWAIT1_SHUTDOWN_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                executor = null;
                executor1 = null;
            }
        });
    }

    private static HttpServerConfig createHttpServerConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        httpConfig.selectors = N_SELECTOR_THREADS;
        return httpConfig;
    }

    private HttpServer createHttpServer(HttpServerConfig httpConfig) throws IOException {
        return new HttpServer(httpConfig) {
            public void handleRequest(Request request, HttpSession session) {
                try {
                    executor.execute(() -> {
                        try {
                            multiplex(request, session);
                        } catch (Exception e) {
                            try {
                                session.sendError(Response.SERVICE_UNAVAILABLE, "Internal error");
                            } catch (IOException e1) {
                                LOG.error("Could not send error to client", e1);
                            }
                        }
                    });
                } catch (RejectedExecutionException e) {
                    try {
                        session.sendError(Response.SERVICE_UNAVAILABLE, "Service is busy");
                    } catch (IOException e1) {
                        LOG.error("Could not send error to client", e1);
                    }
                }
            }

            @Override
            public synchronized void stop() {
                // HttpServer.stop() doesn't close sockets
                for (SelectorThread thread : selectors) {
                    for (Session session : thread.selector) {
                        session.socket().close();
                    }
                }
                executor.shutdown();
                super.stop();
            }
        };
    }

    private void multiplex(Request request, HttpSession session)
            throws IOException, InternalErrorException {
        try {
            switch (request.getPath()) {
                case V0_ENTITY -> {
                    switch (request.getMethod()) {
                        case Request.METHOD_GET -> v0EntityGet(request, session);
                        case Request.METHOD_PUT -> v0EntityPut(request, session);
                        case Request.METHOD_DELETE -> v0EntityDelete(request, session);
                        default -> throw new MethodNotAllowedException();
                    }
                }
                default -> throw new BadPathException();
            }
        } catch (MethodNotAllowedException e) {
            session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
        } catch (BadPathException | InvalidParamsException e) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
        }
    }

    private void v0EntityGet(Request request, HttpSession session)
            throws InvalidParamsException, InternalErrorException {
        executeReplicatingRequest(request, session,
                (id, body) -> executeV0EntityGet(id), this::aggregateV0EntityGet);
    }

    private Response executeV0EntityGet(String id) throws InternalErrorException {
        try {
            byte[] value = db.get(Utf8.toBytes(id));

            if (value == null) {
                Response response = new Response(Response.NOT_FOUND, Response.EMPTY);
                response.addHeader("Timestamp: " + 0);
                return response;
            }

            ByteBuffer buffer = ByteBuffer.wrap(value);

            long timestamp = buffer.getLong();
            byte tombstone = buffer.get();

            if (tombstone == 1) {
                Response response = new Response(Response.NOT_FOUND, Response.EMPTY);
                response.addHeader("Timestamp: " + timestamp);
                return response;
            }

            byte[] body = new byte[buffer.remaining()];
            buffer.get(Long.BYTES, body);

            Response response = new Response(Response.OK, body);
            response.addHeader("Timestamp: " + timestamp);
            return response;
        } catch (RocksDBException e) {
            throw new InternalErrorException();
        }
    }

    private Response aggregateV0EntityGet(List<Response> responses) {
        long timestamp = -1;
        Response response = null;
        for (Response ackResponse : responses) {
            long responseTimestamp
                    = Long.parseLong(ackResponse.getHeader("Timestamp: "));
            if (timestamp < responseTimestamp) {
                timestamp = responseTimestamp;
                response = ackResponse;
            }
        }
        return response;
    }

    private void v0EntityPut(Request request, HttpSession session)
            throws InvalidParamsException, InternalErrorException {
        executeReplicatingRequest(request, session,
                this::executeV0EntityPut, this::aggregateV0EntityPut);
    }

    private Response executeV0EntityPut(String id, byte[] body) throws InternalErrorException {
        try {
            long timestamp = System.currentTimeMillis();

            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + 1 + body.length);
            buffer.putLong(timestamp);
            buffer.put((byte) 0);
            buffer.put(Long.BYTES, body);

            db.put(Utf8.toBytes(id), buffer.array());

            Response response = new Response(Response.CREATED, Response.EMPTY);
            response.addHeader("Timestamp: " + timestamp);
            return response;
        } catch (RocksDBException e) {
            throw new InternalErrorException();
        }
    }

    private Response aggregateV0EntityPut(List<Response> responses) {
        // return 201 Created if all responses is 201 Created
        boolean all201 = true;
        for (Response response : responses) {
            all201 = all201 && response.getStatus() == 201;
        }
        if (all201) {
            return responses.get(0);
        }

        // 504 Not Enough Replicas otherwise
        return new Response("504 Not Enough Replicas", Response.EMPTY);
    }

    private void v0EntityDelete(Request request, HttpSession session)
            throws InvalidParamsException, InternalErrorException {
        executeReplicatingRequest(request, session,
                (id, body) -> executeV0EntityDelete(id), this::aggregateV0EntityDelete);
    }

    private Response executeV0EntityDelete(String id) throws InternalErrorException {
        try {
            long timestamp = System.currentTimeMillis();

            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + 1);
            buffer.putLong(timestamp);
            // tombstone
            buffer.put((byte) 1);

            db.put(Utf8.toBytes(id), buffer.array());

            Response response = new Response(Response.ACCEPTED, Response.EMPTY);
            response.addHeader("Timestamp: " + timestamp);
            return response;
        } catch (RocksDBException e) {
            throw new InternalErrorException();
        }
    }

    private Response aggregateV0EntityDelete(List<Response> responses) {
        // return 202 Accepted if all responses is 202 Accepted
        boolean all202 = true;
        for (Response response : responses) {
            all202 = all202 && response.getStatus() == 202;
        }
        if (all202) {
            return responses.get(0);
        }

        // 504 Not Enough Replicas otherwise
        return new Response("504 Not Enough Replicas", Response.EMPTY);
    }

    private void executeReplicatingRequest(
            Request request,
            HttpSession session,
            Util.RequestExecution requestExecution,
            Function<List<Response>, Response> requestAggregator
    ) throws InvalidParamsException, InternalErrorException {
        String id = request.getParameter("id=");
        Util.requireNotNullAndNotEmpty(id);

        String ackString = request.getParameter("ack=");
        int ack = ackString != null ? Util.parseInt(ackString) : 1;

        String fromString = request.getParameter("from=");
        int from = fromString != null ? Util.parseInt(fromString) : 1;

        if (ack < 1 || from < 1 || ack > from || from > config.clusterUrls().size()) {
            throw new InvalidParamsException();
        }

        // if request is proxied from another node, execute it and leave
        if (request.getHeader("Proxy") != null) {
            Response response = requestExecution.execute(id, request.getBody());
            try {
                session.sendResponse(response);
            } catch (IOException e) {
                throw new InternalErrorException();
            }
            return;
        }

        List<String> urls = keyManager.getNodeIdsByKey(id, from);
        ReplicatingRequestsAggregator replicatingRequestsAggregator
                = new ReplicatingRequestsAggregator(session, ack, from, urls.size(), requestAggregator);

        // if current node in urls, execute request
        if (urls.remove(config.selfUrl())) {
            executor.execute(() -> {
                Response response;
                try {
                    response = requestExecution.execute(id, request.getBody());
                } catch (InternalErrorException e) {
                    replicatingRequestsAggregator.failure();
                    return;
                }
                replicatingRequestsAggregator.success(response);
            });
        }

        // send request to other nodes
        for (String url : urls) {
            HttpRequest proxyRequest = Util.createProxyRequest(url, request);
            client.sendAsync(proxyRequest, HttpResponse.BodyHandlers.ofByteArray())
                    .whenCompleteAsync((javaNetResponse, e) -> {
                        Response oneNioResponse;
                        if (e == null) {
                            try {
                                oneNioResponse = Util.toOneNioResponse(javaNetResponse);
                            } catch (InternalErrorException e1) {
                                replicatingRequestsAggregator.failure();
                                return;
                            }
                            replicatingRequestsAggregator.success(oneNioResponse);
                        } else {
                            replicatingRequestsAggregator.failure();
                        }
                    }, executor1);
        }
    }

    @ServiceFactory(stage = 5, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new RocksDBService(config);
        }
    }
}
