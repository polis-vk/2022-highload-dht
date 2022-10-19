package ok.dht.test.gerasimov;

import ok.dht.test.gerasimov.exception.ServerException;
import ok.dht.test.gerasimov.sharding.ConsistentHash;
import ok.dht.test.gerasimov.sharding.Shard;
import ok.dht.test.gerasimov.sharding.VNode;
import one.nio.http.HttpClient;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class Server extends HttpServer {
    private static final int DEFAULT_THREAD_POOL_SIZE = 32;
    private static final int SELECTOR_POOL_SIZE = Runtime.getRuntime().availableProcessors() / 2;
    private static final int KEEP_A_LIVE_TIME_IN_NANOSECONDS = 0;
    private static final int WORK_QUEUE_CAPACITY = 256;
    private static final String ENTITY_ENDPOINT = "/v0/entity";
    private static final String ADMIN_ENDPOINT = "/v0/admin";
    private static final Set<Integer> ENTITY_ALLOWED_METHODS = Set.of(
            Request.METHOD_GET,
            Request.METHOD_PUT,
            Request.METHOD_DELETE
    );

    private final ExecutorService executorService;
    private final ConsistentHash<String> consistentHash;
    private final ServiceImpl service;

    public Server(int port, ServiceImpl service, ConsistentHash<String> consistentHash) throws IOException {
        super(createHttpServerConfig(port));
        this.executorService = new ThreadPoolExecutor(
                DEFAULT_THREAD_POOL_SIZE,
                DEFAULT_THREAD_POOL_SIZE,
                KEEP_A_LIVE_TIME_IN_NANOSECONDS,
                TimeUnit.NANOSECONDS,
                new ArrayBlockingQueue<>(WORK_QUEUE_CAPACITY)
        );
        this.consistentHash = consistentHash;
        this.service = service;
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
        session.sendResponse(response);
    }

    @Override
    public synchronized void start() {
        super.start();
    }

    @Override
    public synchronized void stop() {
        consistentHash.getShards().forEach(s -> s.getHttpClient().close());
        for (SelectorThread thread : selectors) {
            for (Session session : thread.selector) {
                session.socket().close();
            }
        }
        super.stop();
        executorService.shutdown();
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        try {
            executorService.execute(() -> {
                try {
                    session.sendResponse(handleRequest(request));
                } catch (IOException e) {
                    throw new ServerException("Handler can not handle request", e);
                }
            });
        } catch (RejectedExecutionException e) {
            session.sendResponse(ResponseEntity.serviceUnavailable());
        }
    }

    private Response handleRequest(Request request) {
        try {
            switch (request.getPath().toLowerCase()) {
                case ENTITY_ENDPOINT:
                    String id = request.getParameter("id=");
                    if (id == null) {
                        return ResponseEntity.badRequest("The required <id> parameter was not passed");
                    }

                    if (!ENTITY_ALLOWED_METHODS.contains(request.getMethod())) {
                        return ResponseEntity.methodNotAllowed();
                    }

                    Shard shard = consistentHash.getShardByKey(id);
                    if (shard.getPort() != port) {
                        return shard.getHttpClient().invoke(request);
                    }

                    return switch (request.getMethod()) {
                        case Request.METHOD_GET -> service.handleGetRequest(id);
                        case Request.METHOD_PUT -> service.handlePutRequest(id, request);
                        case Request.METHOD_DELETE -> service.handleDeleteRequest(id);
                        default -> throw new IllegalStateException("Unexpected value: " + request.getMethod());
                    };
                case ADMIN_ENDPOINT:
                    if (Request.METHOD_GET != request.getMethod()) {
                        return ResponseEntity.methodNotAllowed();
                    }

                    if (request.getHeader("From-Main") != null) {
                        return service.handleAdminRequest();
                    }

                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append(service.handleAdminRequest().getBodyUtf8());
                    stringBuilder.append("\n");
                    request.addHeader("From-Main");
                    for (Shard node : consistentHash.getShards()) {
                        if (node.getPort() != port) {
                            stringBuilder.append(node.getHttpClient().invoke(request).getBodyUtf8());
                            stringBuilder.append("\n");
                        }
                    }
                    stringBuilder.append("---------------\n");

                    for (VNode vnode : consistentHash.getVnodes()) {
                        stringBuilder.append(vnode.toString());
                        stringBuilder.append("\n");
                    }

                    return ResponseEntity.ok(stringBuilder.toString());
                default:
                    return ResponseEntity.badRequest("Unsupported path");
            }
        } catch (Exception e) {
            return ResponseEntity.serviceUnavailable(Arrays.toString(e.getStackTrace()));
        }
    }

    private static HttpServerConfig createHttpServerConfig(int port) {
        HttpServerConfig httpServerConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();

        acceptor.port = port;
        acceptor.reusePort = true;
        httpServerConfig.acceptors = new AcceptorConfig[]{acceptor};
        httpServerConfig.selectors = SELECTOR_POOL_SIZE;

        return httpServerConfig;
    }
}
