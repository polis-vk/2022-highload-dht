package ok.dht.test.pashchenko;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.pashchenko.dao.Config;
import ok.dht.test.pashchenko.dao.Entry;
import ok.dht.test.pashchenko.dao.MemorySegmentDao;
import one.nio.async.CustomThreadFactory;
import one.nio.http.*;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import one.nio.util.Utf8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class MyServer extends HttpServer {
    private static final Logger LOG = LoggerFactory.getLogger(MyServer.class);
    private static final String REMOTE_HEADER_TIMESTAMP = "remote-ts";
    private static final String DATA_HEADER_TIMESTAMP = "data-ts";

    private final MemorySegmentDao dao;
    private final Executor daoExecutor;
    private final Executor executorAggregator;
    private final Executor executorRemoteProcess;
    private final HttpClient client;
    private final ServiceConfig config;
    private final List<Node> nodes;
    private final Node currentNode;

    public MyServer(ServiceConfig config) throws IOException {
        super(createConfigFromPort(config.selfPort()));
        this.config = config;
        dao = new MemorySegmentDao(new Config(config.workingDir(), 1048576L));
        executorAggregator = Executors.newFixedThreadPool(1, new CustomThreadFactory(config.selfUrl() + "-aggregator"));
        executorRemoteProcess = Executors.newFixedThreadPool(2, new CustomThreadFactory(config.selfUrl() + "-executorRemoteProcess"));
        daoExecutor = Executors.newFixedThreadPool(4, new CustomThreadFactory(config.selfUrl() + "-daoExecutor"));
        client = HttpClient.newBuilder().executor(Executors.newFixedThreadPool(4, new CustomThreadFactory(config.selfUrl() + "-client-http-my"))).build();

        nodes = new ArrayList<>(config.clusterUrls().size());
        Node currentNode = null;
        for (String url : config.clusterUrls()) {
            Node node = new Node(url);
            nodes.add(node);
            if (url.equals(config.selfUrl())) {
                if (currentNode != null) {
                    throw new IllegalArgumentException("cluster urls not support");
                }
                currentNode = node;
            }
        }
        if (currentNode == null) {
            throw new IllegalArgumentException("cluster urls not support");
        }
        this.currentNode = currentNode;
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
    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (!"/v0/entity".equals(request.getPath())) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        if (request.getMethod() != Request.METHOD_PUT
                && request.getMethod() != Request.METHOD_GET
                && request.getMethod() != Request.METHOD_DELETE) {
            session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
            return;
        }

        String id = request.getParameter("id=");
        if (id == null || id.isEmpty()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        int from;
        int ack;
        try {
            from = getInt(request, "from=", nodes.size());
            ack = getInt(request, "ack=", (nodes.size() / 2) + 1);
            if (ack > from || ack <= 0) {
                session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                return;
            }
        } catch (Exception_400 e) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }
        String remoteHeaderTimestamp = request.getHeader(REMOTE_HEADER_TIMESTAMP + ":");
        if (remoteHeaderTimestamp != null) {
            long timestamp;
            try {
                timestamp = Long.parseLong(remoteHeaderTimestamp);
            } catch (NumberFormatException e) {
                LOG.error("error parse long", e);
                sendError(session);
                return;
            }
            executorRemoteProcess.execute(() -> {
                try {
                    String url = currentNode.url;
                    LOG.debug("Url {} for id {} (my port is {})", url, id, config.selfPort());
                    HandleResult result = handleRequest(request, id, timestamp);
                    Response response = new Response(result.status, result.data);
                    response.addHeader(DATA_HEADER_TIMESTAMP + ":" + result.timestamp);
                    session.sendResponse(response);
                } catch (Exception e) {
                    LOG.error("error handle request", e);
                    sendError(session);
                }
            });
            return;
        }


        int firstIndex = getNodeIndexForKey(id);
        long now = System.currentTimeMillis();
        CompletableFuture[] completableFuturesResults = new CompletableFuture[from];
        for (int i = 0; i < from; i++) {
            int iFinal = i;
            Node node = nodes.get((firstIndex + i) % nodes.size());

            try {
                String url = node.url;
                LOG.debug("Url {} for id {} (my port is {})", url, id, config.selfPort());
                completableFuturesResults[iFinal] =
                        url.equals(config.selfUrl())
                                ? handleRequestAsync(request, id, now)
                                : proxyRequest(request, url, now);
            } catch (Exception e) {
                LOG.error("error handle request", e);
                completableFuturesResults[iFinal] = CompletableFuture.completedFuture(new HandleResult(Response.INTERNAL_ERROR));
            }
        }

        AtomicInteger successAtomic = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        AtomicReference<HandleResult> winResult = new AtomicReference<>(new HandleResult(Response.GATEWAY_TIMEOUT));
        for (CompletableFuture<HandleResult> completableFuture : completableFuturesResults) {
            completableFuture.whenCompleteAsync((result, throwable) -> {
                if (throwable != null) {
                    LOG.error("error complete async", throwable);
                    result = new HandleResult(Response.INTERNAL_ERROR);
                }

                int success = 0;
                if (result.status.equals(Response.OK)
                        || result.status.equals(Response.CREATED)
                        || result.status.equals(Response.ACCEPTED)
                        || result.status.equals(Response.NOT_FOUND)) {
                    boolean compareAndSetResult = false;
                    while (!compareAndSetResult) {
                        HandleResult winResultGet = winResult.get();
                        if (winResultGet.timestamp <= result.timestamp) {
                            compareAndSetResult = winResult.compareAndSet(winResultGet, result);
                        } else {
                            compareAndSetResult = true;
                        }
                    }
                    success = successAtomic.incrementAndGet();
                }


                if (success == ack) {
                    HandleResult winResultGet = winResult.get();
                    try {
                        session.sendResponse(new Response(winResultGet.status, winResultGet.data));
                    } catch (IOException e) {
                        LOG.error("executor sendResponse", e);
                        sessionClose(session);
                        return;
                    }
                    return;
                }
                int completedGet = completed.incrementAndGet();
                if (completedGet == from && success < ack) {
                    try {
                        session.sendResponse(new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY));
                    } catch (IOException e) {
                        LOG.error("executor sendResponse", e);
                        sessionClose(session);
                        return;
                    }
                    return;
                }
            }, executorAggregator);
        }
    }

//    private boolean processNode(Node node, Runnable runnable) {
//
//        int tasks = node.tasksCount.incrementAndGet();
//        if (tasks > Node.MAX_TASKS_ALLOWED) {
//            node.tasksCount.decrementAndGet();
//            return false;
//        }
//
//
//        node.tasks.add(runnable);
//
//
//        if (node.maxWorkersSemaphore.tryAcquire()) {
//            executor.execute(new Runnable() {
//                @Override
//                public void run() {
//                    Runnable poll = node.tasks.poll();
//
//                    if (poll == null) {
//                        node.maxWorkersSemaphore.release();
//                        return;
//                    }
//
//                    try {
//                        poll.run();
//                    } catch (Exception e) {
//                        LOG.error("Unexpected error handle request", e);
//                    } finally {
//                        node.tasksCount.decrementAndGet();
//                        executor.execute(this);
//                    }
//                }
//            });
//        }
//        return true;
//    }

    private int getInt(Request request, String param, int defaultValue) throws Exception_400 {
        String fromStr = request.getParameter(param);
        if (fromStr == null) {
            return defaultValue;
        } else {
            try {
                return Integer.parseInt(fromStr);
            } catch (NumberFormatException e) {
                LOG.error("Error parse int" + param, e);
                throw new Exception_400();
            }
        }
    }

    private int getNodeIndexForKey(String id) {
        int hash = Integer.MAX_VALUE;
        int maxIndex = 0;
        int idHash = id.hashCode() * 17;
        int i = 0;
        for (Node node : nodes) {
            int newHash = idHash + node.url.hashCode();
            if (newHash < hash) {
                hash = newHash;
                maxIndex = i;
            }
            i++;
        }
        return maxIndex;
    }

    private static void sendError(HttpSession session) {
        try {
            session.sendResponse(new Response(Response.INTERNAL_ERROR, Response.EMPTY));
        } catch (Exception ex) {
            LOG.error("error send response", ex);
            sessionClose(session);
        }
    }

    private static void sessionClose(HttpSession session) {
        try {
            session.close();
        } catch (Exception e) {
            LOG.error("error close session", e);
        }
    }

    private CompletableFuture<HandleResult> proxyRequest(Request request, String url, long now) throws IOException, InterruptedException {
        HttpRequest proxyRequest = HttpRequest.newBuilder(URI.create(url + request.getURI()))
                .header(REMOTE_HEADER_TIMESTAMP, String.valueOf(now))
                .method(
                        request.getMethodName(),
                        request.getBody() == null
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofByteArray(request.getBody())
                ).build();

        CompletableFuture<HttpResponse<byte[]>> responseCompletableFuture = client.sendAsync(proxyRequest, HttpResponse.BodyHandlers.ofByteArray());
        return responseCompletableFuture.thenApplyAsync(response -> {
                    String status = switch (response.statusCode()) {
                        case HttpURLConnection.HTTP_OK -> Response.OK;
                        case HttpURLConnection.HTTP_CREATED -> Response.CREATED;
                        case HttpURLConnection.HTTP_ACCEPTED -> Response.ACCEPTED;
                        case HttpURLConnection.HTTP_NO_CONTENT -> Response.NO_CONTENT;
                        case HttpURLConnection.HTTP_SEE_OTHER -> Response.SEE_OTHER;
                        case HttpURLConnection.HTTP_NOT_MODIFIED -> Response.NOT_MODIFIED;
                        case HttpURLConnection.HTTP_USE_PROXY -> Response.USE_PROXY;
                        case HttpURLConnection.HTTP_BAD_REQUEST -> Response.BAD_REQUEST;
                        case HttpURLConnection.HTTP_UNAUTHORIZED -> Response.UNAUTHORIZED;
                        case HttpURLConnection.HTTP_PAYMENT_REQUIRED -> Response.PAYMENT_REQUIRED;
                        case HttpURLConnection.HTTP_FORBIDDEN -> Response.FORBIDDEN;
                        case HttpURLConnection.HTTP_NOT_FOUND -> Response.NOT_FOUND;
                        case HttpURLConnection.HTTP_NOT_ACCEPTABLE -> Response.NOT_ACCEPTABLE;
                        case HttpURLConnection.HTTP_CONFLICT -> Response.CONFLICT;
                        case HttpURLConnection.HTTP_GONE -> Response.GONE;
                        case HttpURLConnection.HTTP_LENGTH_REQUIRED -> Response.LENGTH_REQUIRED;
                        case HttpURLConnection.HTTP_INTERNAL_ERROR -> Response.INTERNAL_ERROR;
                        case HttpURLConnection.HTTP_NOT_IMPLEMENTED -> Response.NOT_IMPLEMENTED;
                        case HttpURLConnection.HTTP_BAD_GATEWAY -> Response.BAD_GATEWAY;
                        case HttpURLConnection.HTTP_GATEWAY_TIMEOUT -> Response.GATEWAY_TIMEOUT;
                        default -> throw new IllegalArgumentException("Unknown status code: " + response.statusCode());
                    };
                    Optional<String> dataHeaderStr = response.headers().firstValue(DATA_HEADER_TIMESTAMP);
                    long timestamp = 0;
                    if (dataHeaderStr.isPresent()) {
                        timestamp = Long.parseLong(dataHeaderStr.get());
                    }
                    return new HandleResult(status, timestamp, response.body());
                }
        , executorAggregator).exceptionally(throwable -> {
            LOG.error("error send async", throwable);
            return new HandleResult(Response.INTERNAL_ERROR);
        });

    }

    private CompletableFuture<HandleResult> handleRequestAsync(Request request, String id, long now) {
        return CompletableFuture.supplyAsync(() -> handleRequest(request, id, now), daoExecutor);
    }

    private HandleResult handleRequest(Request request, String id, long now) {
        switch (request.getMethod()) {
            case Request.METHOD_GET -> {
                Entry entry = dao.get(MemorySegment.ofArray(Utf8.toBytes(id)));
                if (entry == null) {
                    return new HandleResult(Response.NOT_FOUND);
                }
                if (entry.isTombstone()) {
                    return new HandleResult(Response.NOT_FOUND, entry.timestamp());
                }
                return new HandleResult(Response.OK, entry.timestamp(), entry.value().toByteArray());
            }
            case Request.METHOD_PUT -> {
                dao.upsert(new Entry(MemorySegment.ofArray(Utf8.toBytes(id)), MemorySegment.ofArray(request.getBody()), now));
                return new HandleResult(Response.CREATED);
            }
            case Request.METHOD_DELETE -> {
                dao.upsert(new Entry(MemorySegment.ofArray(Utf8.toBytes(id)), null, now));
                return new HandleResult(Response.ACCEPTED);
            }
            default -> {
                return new HandleResult(Response.METHOD_NOT_ALLOWED);
            }
        }
    }

    @Override
    public synchronized void stop() {
        super.stop();
        for (SelectorThread selector : selectors) {
            for (Session session : selector.selector) {
                session.close();
            }
        }
        try {
            dao.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static class Node {
        static final int MAX_TASKS_ALLOWED = 128;
        static final int MAX_WORKERS_ALLOWED = 1;

        final String url;
        final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();

        final AtomicInteger tasksCount = new AtomicInteger(0);
        final Semaphore maxWorkersSemaphore = new Semaphore(MAX_WORKERS_ALLOWED);

        Node(String url) {
            this.url = url;
        }
    }

    static class HandleResult {
        final String status;
        final long timestamp;
        final byte[] data;

        public HandleResult(String status, long timestamp) {
            this.status = status;
            this.timestamp = timestamp;
            this.data = Response.EMPTY;
        }

        public HandleResult(String status, long timestamp, byte[] data) {
            this.status = status;
            this.timestamp = timestamp;
            this.data = data;
        }

        public HandleResult(String status) {
            this.status = status;
            this.timestamp = 0;
            this.data = Response.EMPTY;
        }
    }

}
