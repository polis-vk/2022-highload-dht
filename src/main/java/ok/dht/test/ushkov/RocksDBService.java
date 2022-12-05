package ok.dht.test.ushkov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.ushkov.dao.Entry;
import ok.dht.test.ushkov.dao.EntryIterator;
import ok.dht.test.ushkov.dao.RocksDBDao;
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
import one.nio.net.Socket;
import one.nio.server.AcceptorConfig;
import one.nio.server.RejectedSessionException;
import one.nio.server.SelectorThread;
import one.nio.util.ByteArrayBuilder;
import one.nio.util.Utf8;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class RocksDBService implements Service {
    public static final int N_SELECTOR_THREADS = 3;
    public static final int N_EXECUTOR_THREADS = 3;
    public static final int N_PROXY_EXECUTOR_THREADS = 2;
    public static final int EXECUTOR_QUEUE_CAPACITY = 2000;
    public static final int PROXY_EXECUTOR_QUEUE_CAPACITY = 2000;
    public static final int EXECUTOR_AWAIT_SHUTDOWN_TIMEOUT_MINUTES = 1;
    public static final int PROXY_EXECUTOR_AWAIT_SHUTDOWN_TIMEOUT_MINUTES = 1;
    public static final int NODE_QUEUE_TASKS_LIMIT = 128;
    public static final int NODE_QUEUE_TASKS_ON_EXECUTOR_LIMIT = 3;
    public static final int CHUNK_SIZE = 512;
    
    private static final String V0_ENTITY = "/v0/entity";
    private static final String V0_ENTITIES = "/v0/entities";
    private static final String V0_CLEAN = "/v0/clean";
    private static final Logger LOG = LoggerFactory.getLogger(RocksDBService.class);

    private final ServiceConfig config;
    private final KeyManager keyManager = new ConsistentHashing();
    private final AtomicBoolean clean = new AtomicBoolean(false);

    public RocksDBDao dao;
    private HttpServer httpServer;

    private ExecutorService executor;
    private ExecutorService proxyExecutor;
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
                N_EXECUTOR_THREADS,
                N_EXECUTOR_THREADS,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(EXECUTOR_QUEUE_CAPACITY)
        );

        proxyExecutor = new ThreadPoolExecutor(
                N_PROXY_EXECUTOR_THREADS,
                N_PROXY_EXECUTOR_THREADS,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(PROXY_EXECUTOR_QUEUE_CAPACITY)
        );

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(500))
                .version(HttpClient.Version.HTTP_1_1)
                .executor(executor)
                .build();

        try {
            dao = new RocksDBDao(RocksDB.open(config.workingDir().toString()));
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
        if (httpServer == null && dao == null
                && executor == null && proxyExecutor == null) {
            return CompletableFuture.completedFuture(null);
        }

        LOG.debug("stop node {}", config.selfUrl());

        httpServer.stop();
        httpServer = null;

        try {
            dao.getDb().closeE();
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
        dao = null;

        executor.shutdown();
        proxyExecutor.shutdown();

        return CompletableFuture.runAsync(() -> {
            try {
                executor.awaitTermination(EXECUTOR_AWAIT_SHUTDOWN_TIMEOUT_MINUTES, TimeUnit.MINUTES);
                proxyExecutor.awaitTermination(PROXY_EXECUTOR_AWAIT_SHUTDOWN_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                executor = null;
                proxyExecutor = null;
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
            @Override
            public HttpSession createSession(Socket socket) throws RejectedSessionException {
                return new HttpSession(socket, this) {
                    @Override
                    public synchronized void sendResponse(Response response) throws IOException {
                        if (response instanceof Chunk) {
                            super.write(new QueueItem() {
                                @Override
                                public int write(Socket socket) throws IOException {
                                    byte[] body = response.getBody();
                                    return socket.write(body, 0, body.length);
                                }
                            });
                        } else {
                            super.sendResponse(response);
                        }
                    }
                };
            }

            public void handleRequest(Request request, HttpSession session) {
                try {
                    executor.execute(() -> {
                        try {
                            if (clean.get()) {
                                session.sendResponse(new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY));
                                return;
                            }
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
                case V0_ENTITIES -> {
                    switch (request.getMethod()) {
                        case Request.METHOD_GET -> v0EntitiesGet(request, session);
                        default -> throw new MethodNotAllowedException();
                    }
                }
                case V0_CLEAN -> {
                    switch (request.getMethod()) {
                        case Request.METHOD_GET -> v0Clean(request, session);
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
                (id, body, timestamp, ttl) -> executeV0EntityGet(id, timestamp), this::aggregateV0EntityGet);
    }

    private Response executeV0EntityGet(String id, long timestamp) throws InternalErrorException {
        try {
            Entry entry = dao.get(Utf8.toBytes(id), timestamp);
            if (entry.value() == null) {
                Response response = new Response(Response.NOT_FOUND, Response.EMPTY);
                response.addHeader("Timestamp: " + entry.timestamp());
                return response;
            } else {
                Response response = new Response(Response.OK, entry.value());
                response.addHeader("Timestamp: " + entry.timestamp());
                return response;
            }
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

    private Response executeV0EntityPut(String id, byte[] body, long timestamp, long ttl) throws InternalErrorException {
        try {
            dao.put(Utf8.toBytes(id), body, timestamp, ttl);

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
                (id, body, timestamp, ttl) -> executeV0EntityDelete(id, timestamp), this::aggregateV0EntityDelete);
    }

    private Response executeV0EntityDelete(String id, long timestamp) throws InternalErrorException {
        try {
            dao.delete(Utf8.toBytes(id), timestamp);

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
        if (id == null || id.isEmpty()) {
            throw new InvalidParamsException();
        }

        String ackString = request.getParameter("ack=");
        int ack = ackString != null ? Util.parseInt(ackString) : 1;

        String fromString = request.getParameter("from=");
        int from = fromString != null ? Util.parseInt(fromString) : 1;

        if (ack < 1 || from < 1 || ack > from || from > config.clusterUrls().size()) {
            throw new InvalidParamsException();
        }

        String ttlString = request.getParameter("ttl=");
        long ttl = ttlString != null ? Util.parseLong(ttlString) : 0;

        // if request is proxied from another node, execute it and leave
        if (request.getHeader("Proxy: ") != null) {
            long timestamp;
            try {
                timestamp = Long.parseLong(request.getHeader("Proxy: "));
            } catch (NumberFormatException e) {
                throw new InternalErrorException();
            }
            Response response = requestExecution.execute(id, request.getBody(), timestamp, ttl);
            try {
                session.sendResponse(response);
            } catch (IOException e) {
                throw new InternalErrorException();
            }
            return;
        }

        long timestamp = System.currentTimeMillis();

        List<String> urls = keyManager.getNodeIdsByKey(id, from);
        ReplicatingRequestsAggregator replicatingRequestsAggregator
                = new ReplicatingRequestsAggregator(session, ack, from, urls.size(), requestAggregator);

        // if current node in urls, execute request
        if (urls.remove(config.selfUrl())) {
            executor.execute(() -> {
                Response response;
                try {
                    response = requestExecution.execute(id, request.getBody(), timestamp, ttl);
                } catch (InternalErrorException e) {
                    replicatingRequestsAggregator.failure();
                    return;
                }
                replicatingRequestsAggregator.success(response);
            });
        }

        // send request to other nodes
        for (String url : urls) {
            HttpRequest proxyRequest = Util.createProxyRequest(url, request, timestamp);
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
                    }, proxyExecutor);
        }
    }

    private void v0EntitiesGet(Request request, HttpSession session)
            throws InvalidParamsException, InternalErrorException {
        String start = request.getParameter("start=");
        if (start == null || start.isEmpty()) {
            throw new InvalidParamsException();
        }

        String end = request.getParameter("end=");
        if (end != null && end.isEmpty()) {
            throw new InvalidParamsException();
        }

        if (end != null && start.compareTo(end) > 0) {
            throw new InvalidParamsException();
        }

        executeRangeRequest(session, start, end);
    }

    private void executeRangeRequest(HttpSession session, String start, String end) throws InternalErrorException {
        EntryIterator iterator = end != null
                ? dao.range(Utf8.toBytes(start), Utf8.toBytes(end))
                : dao.range(Utf8.toBytes(start));
        try {
            session.sendResponse(new ChunkedResponseHeaders(Response.OK));
        } catch (IOException e) {
            throw new InternalErrorException();
        }
        executor.execute(() -> {
            ByteArrayBuilder chunk = new ByteArrayBuilder(CHUNK_SIZE);
            while (iterator.hasNext()) {
                Entry entry = iterator.next();
                int size = entry.key().length + 1 + entry.value().length + 1;
                if (CHUNK_SIZE - chunk.length() < size) {
                    try {
                        session.sendResponse(Chunk.newChunk(chunk.toBytes()));
                    } catch (IOException e) {
                        // No ops.
                    }
                }
                chunk.append(entry.key());
                chunk.append((byte) '\n');
                chunk.append(entry.value());
            }
            if (chunk.length() != 0) {
                try {
                    session.sendResponse(Chunk.newChunk(chunk.toBytes()));
                } catch (IOException e) {
                    // No ops.
                }
            }
            try {
                session.sendResponse(Chunk.newLastChunk());
            } catch (IOException e) {
                // No ops.
            }
        });
    }

    private void v0Clean(Request request, HttpSession session) throws IOException {
        if (!clean.compareAndSet(false, true)) {
            session.sendResponse(new Response(Response.NOT_FOUND, Response.EMPTY));
            return;
        }
        try {
            session.sendResponse(new Response(Response.OK, Response.EMPTY));
            executor.execute(() -> {
                long timestamp = System.currentTimeMillis();
                EntryIterator it = dao.range();
                while (it.hasNext()) {
                    Entry entry = it.next();
                    if (entry.timestamp() + entry.ttl() < timestamp
                            || entry.value() == null) {
                        try {
                            dao.db.delete(entry.key());
                        } catch (RocksDBException e) {
                            LOG.error("Could not delete entry", e);
                        }
                    }
                }
            });
        } finally {
            clean.set(false);
        }
    }

    @ServiceFactory(stage = 7, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new RocksDBService(config);
        }
    }
}
