package ok.dht.test.garanin;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.garanin.db.Db;
import ok.dht.test.garanin.db.DbException;
import ok.dht.test.garanin.db.DbIterator;
import ok.dht.test.garanin.db.Value;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.server.AcceptorConfig;
import one.nio.server.RejectedSessionException;
import org.rocksdb.RocksDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class DhtService implements Service {

    private static final Logger LOGGER = LoggerFactory.getLogger(DhtServer.class);
    private static final Duration TIMEOUT = Duration.ofMillis(500);
    private static final int MAX_PROXY_REQUEST_PER_NODE = 500;

    private static final Set<Integer> ALLOWED_METHODS = Set.of(
            Request.METHOD_GET,
            Request.METHOD_PUT,
            Request.METHOD_DELETE
    );

    private final ServiceConfig config;
    private final ExecutorService selfExecutor = new ForkJoinPool();
    private final ExecutorService proxyExecutor = new ForkJoinPool();
    private final HttpClient httpClient;
    private final Node[] nodes;
    private HttpServer server;
    private RocksDB rocksDB;

    public DhtService(ServiceConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .executor(proxyExecutor)
                .build();
        this.nodes = config.clusterUrls().stream().map(url -> new Node(hash(url), url)).toArray(Node[]::new);
    }

    private static int hash(String str) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(str.getBytes(Charset.defaultCharset()));
            return Math.abs(ByteBuffer.wrap(bytes).getInt());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Node> getUrls(String id, int count) {
        int hash = hash(id);
        int index = Arrays.binarySearch(nodes, new Node(hash, null), Comparator.comparing(Node::hash));
        if (index < 0) {
            index = (-index - 1) % nodes.length;
        }
        return (index + count - 1 < nodes.length
                ? Arrays.stream(nodes)
                : Stream.concat(Arrays.stream(nodes), Arrays.stream(nodes)))
                .skip(index).limit(count).toList();
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        rocksDB = Db.open(config.workingDir());
        server = new DhtHttpServer(createConfigFromPort(config.selfPort()));
        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        if (rocksDB != null) {
            Db.close(rocksDB);
        }
        rocksDB = null;
        return CompletableFuture.completedFuture(null);
    }

    public Response handleGet(String id) {
        try {
            Value value = new Value(Db.get(rocksDB, id));
            if (value.tombstone()) {
                return new Response(Response.NOT_FOUND, value.toBytes());
            }
            return new Response(Response.OK, value.toBytes());
        } catch (DbException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }

    }

    public Response handlePut(Request request, String id, long timestamp) {
        try {
            Db.put(rocksDB, id, new Value(request.getBody(), timestamp).toBytes());
        } catch (DbException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
        return new Response(Response.CREATED, Response.EMPTY);
    }

    public Response handleDelete(String id, long timestamp) {
        try {
            Db.put(rocksDB, id, new Value(timestamp).toBytes());
        } catch (DbException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    private class DhtHttpServer extends HttpServer {

        public DhtHttpServer(HttpServerConfig config, Object... routers) throws IOException {
            super(config, routers);
        }

        @SuppressWarnings("FutureReturnValueIgnored")
        @Override
        public void handleRequest(Request request, HttpSession session) throws IOException {
            switch (request.getPath()) {
                case "/v0/entity" -> handleEntity(request, session);
                case "/v0/entities" -> handleEntities(request, session);
                default -> session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            }
        }

        @Override
        public synchronized void stop() {
            for (var selectorThread : selectors) {
                if (selectorThread.selector.isOpen()) {
                    for (var session : selectorThread.selector) {
                        session.close();
                    }
                }
            }
            super.stop();
        }

        @Override
        public HttpSession createSession(Socket socket) throws RejectedSessionException {
            return new HttpSession(socket, this) {
                @Override
                protected void writeResponse(Response response, boolean includeBody) throws IOException {
                    if (response instanceof ChunkedResponse) {
                        super.writeResponse(response, false);
                        var iterator = ((ChunkedResponse) response).getIterator();
                        super.write(new ChunkedQueueItem(iterator));
                    } else {
                        super.writeResponse(response, includeBody);
                    }
                }
            };
        }
    }

    private void handleEntity(Request request, HttpSession session) throws IOException {
        if (!request.getPath().equals("/v0/entity")) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }
        if (!ALLOWED_METHODS.contains(request.getMethod())) {
            session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
            return;
        }
        String id = request.getParameter("id=");
        if (id == null || !validateId(id)) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }
        String ackString = request.getParameter("ack=");
        String fromString = request.getParameter("from=");
        String timestampString = request.getParameter("timestamp=");
        try {
            int ack = ackString == null ? nodes.length / 2 + 1 : Integer.parseInt(ackString);
            int from = fromString == null ? nodes.length : Integer.parseInt(fromString);
            if (!validateReplicasCnt(ack, from)) {
                session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                return;
            }
            long timestamp = timestampString != null
                    ? Long.parseLong(timestampString)
                    : System.currentTimeMillis();
            selfExecutor.execute(() -> {
                if (timestampString != null) {
                    Response response = switch (request.getMethod()) {
                        case Request.METHOD_GET -> handleGet(id);
                        case Request.METHOD_PUT -> handlePut(request, id, timestamp);
                        case Request.METHOD_DELETE -> handleDelete(id, timestamp);
                        default -> throw new IllegalStateException();
                    };
                    sendResponse(session, response);
                    return;
                }
                List<Node> replicas = getUrls(id, from);
                ResponsesMerger responseMerger = new ResponsesMerger(session, ack, from);
                for (Node node: replicas) {
                    if (!node.url().equals(config.selfUrl())) {
                        if (node.requestCount().decrementAndGet() > 0) {
                            HttpRequest proxyRequest = HttpRequest.newBuilder()
                                    .uri(URI.create("%s/v0/entity?id=%s&ack=1&from=1&timestamp=%s"
                                            .formatted(node.url(), id, timestamp)))
                                    .method(request.getMethodName(), request.getBody() == null
                                            ? HttpRequest.BodyPublishers.noBody()
                                            : HttpRequest.BodyPublishers.ofByteArray(request.getBody()))
                                    .build();
                            httpClient.sendAsync(proxyRequest, HttpResponse.BodyHandlers.ofByteArray())
                                    .exceptionally(ex -> null)
                                    .thenAccept(response -> {
                                        responseMerger.acceptJava(response);
                                        node.requestCount().incrementAndGet();
                                    });
                        } else {
                            responseMerger.acceptNio(null);
                            node.requestCount().incrementAndGet();
                        }
                    } else {
                        selfExecutor.execute(() -> {
                            Response response = switch (request.getMethod()) {
                                case Request.METHOD_GET -> handleGet(id);
                                case Request.METHOD_PUT -> handlePut(request, id, timestamp);
                                case Request.METHOD_DELETE -> handleDelete(id, timestamp);
                                default -> throw new IllegalStateException();
                            };
                            responseMerger.acceptNio(response);
                        });
                    }
                }
            });
        } catch (NumberFormatException ignored) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
        }
    }

    private void handleEntities(Request request, HttpSession session) throws IOException {
        if (!request.getPath().equals("/v0/entities")) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }
        if (request.getMethod() != Request.METHOD_GET) {
            session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
            return;
        }
        String start = request.getParameter("start=");
        String end = request.getParameter("end=");
        if (start == null || !validateId(start) || (end != null && !validateId(end))) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }
        try {
            selfExecutor.execute(() -> {
                var iterator = Db.range(rocksDB, start, end);
                try {
                    session.sendResponse(new ChunkedResponse(iterator, Response.OK));
                } catch (IOException e) {
                    session.close();
                }
            });
        } catch (NumberFormatException ignored) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
        }
    }

    private static boolean validateId(String id) {
        return !id.isEmpty();
    }

    private boolean validateReplicasCnt(int ack, int from) {
        return ack > 0 && ack <= from;
    }

    private static void sendResponse(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
            session.close();
        }
    }

    @ServiceFactory(stage = 6, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new DhtService(config);
        }
    }

    private static class Node {
        private final int hash;
        private final String url;
        private final AtomicInteger requestCount;

        public Node(int hash, String url) {
            this.hash = hash;
            this.url = url;
            this.requestCount = new AtomicInteger(MAX_PROXY_REQUEST_PER_NODE);
        }

        public int hash() {
            return hash;
        }

        public String url() {
            return url;
        }

        public AtomicInteger requestCount() {
            return requestCount;
        }
    }

    private static class ChunkedResponse extends Response {
        private final DbIterator iterator;

        public ChunkedResponse(DbIterator iterator, String resultCode) {
            super(resultCode);
            this.iterator = iterator;
            this.addHeader("Transfer-Encoding: chunked");
        }

        public DbIterator getIterator() {
            return iterator;
        }
    }

    private static class ChunkedQueueItem extends Session.QueueItem {
        private final DbIterator iterator;
        private final ByteBuffer buffer = ByteBuffer.allocate(1024);

        private boolean remaining = true;

        public ChunkedQueueItem(DbIterator iterator) {
            this.iterator = iterator;
            buffer.flip();
        }

        @Override
        public int write(Socket socket) throws IOException {
            if (buffer.remaining() == 0) {
                remaining = iterator.fillBuffer(buffer);
                buffer.flip();
            }
            return socket.write(buffer);
        }

        @Override
        public int remaining() {
            return remaining || buffer.remaining() > 0 ? 1 : 0;
        }
    }
}
