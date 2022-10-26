package ok.dht.test.ushkov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import one.nio.http.HttpClient;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.ConnectionString;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import one.nio.util.Utf8;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class RocksDBService implements Service {
    public static final int N_SELECTOR_THREADS = 4;
    public static final int N_WORKER_THREADS = 4;
    public static final int EXECUTOR_QUEUE_CAPACITY = 100;
    public static final int EXECUTOR_AWAIT_SHUTDOWN_TIMEOUT_MINUTES = 1;
    public static final int NODE_QUEUE_TASKS_LIMIT = 128;
    public static final int NODE_QUEUE_TASKS_ON_EXECUTOR_LIMIT = 3;

    private static final String V0_ENTITY = "/v0/entity";

    private final ServiceConfig config;
    private final KeyManager keyManager = new ConsistentHashing();
    private final Map<String, NodeQueue> nodeQueues = new HashMap<>();

    private ExecutorService executor;
    private RocksDB db;
    private HttpServer httpServer;
    private final Map<String, HttpClient> clientPool = new HashMap<>();

    private static final Logger LOG = LoggerFactory.getLogger(RocksDBService.class);

    public RocksDBService(ServiceConfig config) {
        this.config = config;

        for (String url : config.clusterUrls()) {
            keyManager.addNode(url);
        }

        for (String url : config.clusterUrls()) {
            nodeQueues.put(url, new NodeQueue());
        }
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        LOG.debug("start {}", config.selfUrl());
        executor = new ThreadPoolExecutor(
                N_WORKER_THREADS,
                N_WORKER_THREADS,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(EXECUTOR_QUEUE_CAPACITY)
        );

        try {
            db = RocksDB.open(config.workingDir().toString());
        } catch (RocksDBException e) {
            throw new IOException(e);
        }

        HttpServerConfig httpServerConfig =
                createHttpServerConfigFromPort(config.selfPort());
        httpServer = createHttpServer(httpServerConfig);
        httpServer.start();

        for (String url : config.clusterUrls()) {
            ConnectionString conn = new ConnectionString(url);
            HttpClient client = new HttpClient(conn);
            clientPool.put(url, client);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        LOG.debug("stop {}", config.selfUrl());
        if (httpServer == null && db == null) {
            return CompletableFuture.completedFuture(null);
        }

        clientPool.forEach((url, client) -> client.close());
        clientPool.clear();

        httpServer.stop();
        httpServer = null;

        try {
            db.closeE();
        } catch (RocksDBException e) {
            throw new IOException(e);
        }
        db = null;

        executor.shutdown();

        return CompletableFuture.runAsync(() -> {
            try {
                executor.awaitTermination(EXECUTOR_AWAIT_SHUTDOWN_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                executor = null;
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
                executor.execute(() -> {
                    try {
                        RocksDBService.this.handleRequest(request, session);
                    } catch (Exception e) {
                        try {
                            session.sendError(Response.SERVICE_UNAVAILABLE, "Internal error");
                        } catch (IOException e1) {
                            LOG.error("Could not send error to client", e1);
                        }
                    }
                });
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

    private void handleRequest(Request request, HttpSession session) throws IOException, RocksDBException {
        try {
            switch (request.getPath()) {
                case V0_ENTITY -> {
                    switch (request.getMethod()) {
                        case Request.METHOD_GET -> entityGet(request, session);
                        case Request.METHOD_PUT -> entityPut(request, session);
                        case Request.METHOD_DELETE -> entityDelete(request, session);
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

    private void entityGet(Request request, HttpSession session)
            throws InvalidParamsException, IOException, RocksDBException {
        String key = request.getParameter("id=");
        if (key == null || key.isEmpty()) {
            throw new InvalidParamsException();
        }

        List<String> urls = keyManager.getNodeIdsByKey(key, 1);

        int[] ackFrom = getAckFrom(request);
        int ack = ackFrom[0];
        int from = ackFrom[1];

        if (ack == 1 && from == 1 && urls.get(0).equals(config.selfUrl()) || request.getHeader("Proxy: ") != null) {
            byte[] value = db.get(Utf8.toBytes(key));

            if (value == null) {
                Response response = new Response(Response.NOT_FOUND, Response.EMPTY);
                response.addHeader("Timestamp: " + 0);
                session.sendResponse(response);
                return;
            }

            ByteBuffer buffer = ByteBuffer.wrap(value);

            long timestamp = buffer.getLong();
            byte tombstone = buffer.get();

            if (tombstone == 1) {
                Response response = new Response(Response.NOT_FOUND, Response.EMPTY);
                response.addHeader("Timestamp: " + timestamp);
                session.sendResponse(response);
                return;
            }

            byte[] body = new byte[buffer.remaining()];
            buffer.get(Long.BYTES, body);

            Response response = new Response(Response.OK, body);
            response.addHeader("Timestamp: " + timestamp);
            session.sendResponse(response);
        } else {
            replicateRequest(request, session, key, ackFrom,
                    response -> response.getStatus() == 200 || response.getStatus() == 404);
        }
    }

    private void entityPut(Request request, HttpSession session)
            throws InvalidParamsException, IOException, RocksDBException {
        String key = request.getParameter("id=");
        if (key == null || key.isEmpty()) {
            throw new InvalidParamsException();
        }

        List<String> urls = keyManager.getNodeIdsByKey(key, 1);

        int[] ackFrom = getAckFrom(request);
        int ack = ackFrom[0];
        int from = ackFrom[1];

        if (ack == 1 && from == 1 && urls.get(0).equals(config.selfUrl()) || request.getHeader("Proxy: ") != null) {
            long timestamp = System.currentTimeMillis();
            byte[] body = request.getBody();

            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + 1 + body.length);
            buffer.putLong(timestamp);
            buffer.put((byte) 0);
            buffer.put(Long.BYTES, body);

            db.put(Utf8.toBytes(key), buffer.array());

            Response response = new Response(Response.CREATED, Response.EMPTY);
            response.addHeader("Timestamp: " + timestamp);
            session.sendResponse(response);
        } else {
            replicateRequest(request, session, key, ackFrom,
                    response -> response.getStatus() == 201);
        }
    }

    private void entityDelete(Request request, HttpSession session)
            throws IOException, RocksDBException, InvalidParamsException {
        String key = request.getParameter("id=");
        if (key == null || key.isEmpty()) {
            throw new InvalidParamsException();
        }

        List<String> urls = keyManager.getNodeIdsByKey(key, 1);

        int[] ackFrom = getAckFrom(request);
        int ack = ackFrom[0];
        int from = ackFrom[1];

        if (ack == 1 && from == 1 && urls.get(0).equals(config.selfUrl()) || request.getHeader("Proxy: ") != null) {
            long timestamp = System.currentTimeMillis();

            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + 1);
            buffer.putLong(timestamp);
            // tombstone
            buffer.put((byte) 1);

            db.put(Utf8.toBytes(key), buffer.array());

            Response response = new Response(Response.ACCEPTED, Response.EMPTY);
            response.addHeader("Timestamp: " + timestamp);
            session.sendResponse(response);
        } else {
            replicateRequest(request, session, key, ackFrom,
                    response -> response.getStatus() == 202);
        }
    }

    private int[] getAckFrom(Request request) throws InvalidParamsException {
        int ack;
        try {
            ack = request.getParameter("ack=") != null
                    ? Integer.parseInt(request.getParameter("ack="))
                    : 1;
        } catch (NumberFormatException e) {
            throw new InvalidParamsException();
        }
        int from;
        try {
            from = request.getParameter("from=") != null
                    ? Integer.parseInt(request.getParameter("from="))
                    : 1;
        } catch (NumberFormatException e) {
            throw new InvalidParamsException();
        }

        if (ack < 1 || from < 1 || ack > from || from > config.clusterUrls().size()) {
            throw new InvalidParamsException();
        }

        return new int[]{ack, from};
    }

    private void replicateRequest(
            Request request,
            HttpSession session,
            String key,
            int[] ackFrom,
            Predicate<Response> succeededResponse
    ) throws IOException {
        int ack = ackFrom[0];
        int from = ackFrom[1];

        LOG.debug("{} start replica request, method {}", config.selfUrl(), request.getMethod());

        List<String> urls = keyManager.getNodeIdsByKey(key, from);

        ReplicatedRequest replicatedRequest = new ReplicatedRequest(ack, from);

        for (String url : urls) {
            NodeQueue nodeQueue = nodeQueues.get(url);

            int taskCount = nodeQueue.tasksCount.incrementAndGet();
            if (taskCount > NODE_QUEUE_TASKS_LIMIT) {
                nodeQueue.tasksCount.decrementAndGet();
                session.sendResponse(new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY));
                return;
            }

            nodeQueue.tasks.offer(new Runnable() {
                @Override
                public void run() {
                    Request newRequest = new Request(request);
                    newRequest.addHeader("Proxy: 1");
                    try (HttpClient client = new HttpClient(new ConnectionString(url))) {
                        LOG.debug("from {} to {}", config.selfUrl(), url);
                        Response response = client.invoke(newRequest, 5000);
                        if (succeededResponse.test(response)) {
                            LOG.debug("success {} {} {}", response.getStatus(),
                                    response.getHeader("Timestamp: "), config.selfUrl());
                            replicatedRequest.onSuccess(session, response);
                        } else {
                            LOG.debug("failure {} {} {}", response.getStatus(),
                                    response.getHeader("Timestamp: "), config.selfUrl());
                            replicatedRequest.onFailure(session);
                        }
                    } catch (Exception e1) {
                        LOG.debug("failure {} {}", e1, config.selfUrl());
                        replicatedRequest.onFailure(session);
                    }
                }
            });

            if (taskCount <= NODE_QUEUE_TASKS_ON_EXECUTOR_LIMIT) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        Runnable task = nodeQueue.tasks.poll();
                        if (task != null) {
                            try {
                                task.run();
                            } finally {
                                nodeQueue.tasksCount.decrementAndGet();
                                executor.execute(this);
                            }
                        }
                    }
                });
            }
        }
    }

    private static class FlowControlException extends Exception {
    }

    private static class MethodNotAllowedException extends FlowControlException {
    }

    private static class BadPathException extends FlowControlException {
    }

    private static class InvalidParamsException extends FlowControlException {
    }

    private static class NodeQueue {
        final AtomicInteger tasksCount = new AtomicInteger(0);
        final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    }

    @ServiceFactory(stage = 4, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new RocksDBService(config);
        }
    }
}
