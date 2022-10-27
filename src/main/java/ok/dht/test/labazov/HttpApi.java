package ok.dht.test.labazov;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.labazov.dao.BaseEntry;
import ok.dht.test.labazov.dao.Config;
import ok.dht.test.labazov.dao.Dao;
import ok.dht.test.labazov.dao.Entry;
import ok.dht.test.labazov.dao.MemorySegmentDao;
import ok.dht.test.labazov.hash.ConsistentHash;
import ok.dht.test.labazov.hash.Node;
import one.nio.http.HttpClient;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.net.ConnectionString;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import one.nio.util.Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public final class HttpApi extends HttpServer {
    private static final int FLUSH_THRESHOLD_BYTES = 8 * 1024 * 1024;
    private static final Logger LOG = LoggerFactory.getLogger(HttpApi.class);
    private final ConsistentHash shards;
    private final ServiceConfig config;
    private Dao<MemorySegment, Entry<MemorySegment>> dao;
    private ExecutorService threadPool;

    public HttpApi(ServiceConfig config) throws IOException {
        super(createConfigFromPort(config.selfPort()));
        shards = new ConsistentHash();
        for (final String url : config.clusterUrls()) {
            final int virtualNodeCount = 1;

            final HashSet<Integer> nodeSet = new HashSet<>(virtualNodeCount);
            for (int i = 0; i < virtualNodeCount; i++) {
                nodeSet.add(Hash.murmur3(url));
            }

            shards.addShard(new Node(url), nodeSet);
        }
        this.config = config;
    }

    private static void ioExceptionHandler(HttpSession session) {
        try {
            session.sendResponse(getEmptyResponse(Response.INTERNAL_ERROR));
        } catch (IOException ex) {
            LOG.error("IOException during error reporting: " + ex.getMessage());
        }
    }

    private static Response getEmptyResponse(final String httpCode) {
        return new Response(httpCode, Response.EMPTY);
    }

    private static MemorySegment fromString(final String data) {
        return MemorySegment.ofArray(data.toCharArray());
    }

    private static HttpServerConfig createConfigFromPort(final int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    private static Response appendTs(final Response response, final long ts) {
        response.addHeader("Timestamp: " + ts);
        return response;
    }

    private static byte[] prefixWrite(final byte[] bytes) {
        final long ts = System.currentTimeMillis();
        final boolean isEmpty = bytes == null;
        final ByteBuffer bb = ByteBuffer.allocate(Long.BYTES + 1 + (isEmpty ? 0 : bytes.length));
        bb.putLong(ts);
        if (isEmpty) {
            bb.put((byte) 0);
        } else {
            bb.put((byte) 1);
            bb.put(bytes);
        }
        return bb.array();
    }

    private static int toIntOrDefault(final String str) {
        if (str == null) {
            return 1;
        }
        return Integer.parseInt(str);
    }

    @Override
    public synchronized void start() {
        try {
            dao = new MemorySegmentDao(new Config(config.workingDir(), FLUSH_THRESHOLD_BYTES));
            int nproc = Runtime.getRuntime().availableProcessors();
            threadPool = Executors.newFixedThreadPool(nproc / 2 + 1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        super.start();
    }

    @Override
    public synchronized void stop() {
        for (SelectorThread thread : selectors) {
            for (Session session : thread.selector) {
                session.close();
            }
        }

        super.stop();
        shutdownAndAwaitTermination(threadPool);
        try {
            dao.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Taken from javadoc
    private static void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                pool.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                    LOG.error("Pool did not terminate");
                }
            }
        } catch (InterruptedException ex) {
            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        try {
            threadPool.execute(() -> jobHandler(request, session));
        } catch (RejectedExecutionException e) {
            session.sendResponse(getEmptyResponse(Response.SERVICE_UNAVAILABLE));
            LOG.error("RejectedExecutionException during job submission: " + e.getMessage());
        }
    }

    private void jobHandler(Request request, HttpSession session) {
        try {
            super.handleRequest(request, session);
        } catch (IOException e) {
            LOG.error("IOException during request handling: ", e);
            ioExceptionHandler(session);
        } catch (Exception e) {
            LOG.error("Exception during request handling: ", e);
            genericExceptionHandler(request, session);
        }
    }

    private void forwardRequestByKey(final Request request,
                                     final HttpSession session,
                                     final String key,
                                     final int acks,
                                     final int froms) throws IOException {
        final List<Node> targetShards = shards.getShards(key, froms);
        final RequestGather gather = new RequestGather(acks, froms);
        for (final Node shard : targetShards) {
            int tasks = shard.tasksCount.incrementAndGet();
            if (tasks > Node.MAX_TASKS_ALLOWED) {
                shard.tasksCount.decrementAndGet();
                session.sendResponse(getEmptyResponse(Response.SERVICE_UNAVAILABLE));
                return;
            }

            shard.tasks.offer(() -> {
                Request redirectedRequest = new Request(request);
                redirectedRequest.addHeader("Proxy: true");
                try (final HttpClient httpClient = new HttpClient(new ConnectionString(shard.url))) {
                    final Response response = httpClient.invoke(redirectedRequest, 500);
                    switch (response.getStatus()) {
                        case 200, 201, 202, 404 -> gather.submitGoodResponse(session, response);
                        default -> gather.submitFailure(session);
                    }
                } catch (Exception e) {
                    LOG.error("Exception during forwarding", e);
                    gather.submitFailure(session);
                }
            });

            if (tasks <= Node.MAX_WORKERS_ALLOWED) {
                threadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        Runnable poll = shard.tasks.poll();
                        if (poll != null) {
                            try {
                                poll.run();
                            } catch (Exception e) {
                                LOG.error("Unexpected error handle request", e);
                            } finally {
                                shard.tasksCount.decrementAndGet();
                                threadPool.execute(this);
                            }
                        }
                    }
                });
            }
        }
    }

    private void genericExceptionHandler(Request request, HttpSession session) {
        try {
            handleDefault(request, session);
        } catch (IOException ex) {
            LOG.error("IOException during error reporting: " + ex.getMessage());
        }
    }

    @Override
    public void handleDefault(final Request request, final HttpSession session) throws IOException {
        final String code;
        switch (request.getMethod()) {
            case Request.METHOD_GET, Request.METHOD_PUT, Request.METHOD_DELETE -> code = Response.BAD_REQUEST;
            default -> code = Response.METHOD_NOT_ALLOWED;
        }
        session.sendResponse(getEmptyResponse(code));
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public void handleGet(@Param(value = "id", required = true) final String key,
                          @Param(value = "ack") final String ack,
                          @Param(value = "from") final String from,
                          final Request req,
                          final HttpSession session) throws IOException {
        if (key.isEmpty()) {
            session.sendResponse(getEmptyResponse(Response.BAD_REQUEST));
            return;
        }
        if (shouldBeForwarded(key, ack, from, req, session)) return;
        final Entry<MemorySegment> result = dao.get(fromString(key));
        if (result == null) {
            session.sendResponse(appendTs(new Response(Response.NOT_FOUND, Response.EMPTY), 0));
        } else {
            final var storedValue = ByteBuffer.wrap(result.value().toByteArray());
            final long ts = storedValue.getLong();
            final byte hasData = storedValue.get();

            if (hasData == 1) {
                byte[] realValue = new byte[storedValue.remaining()];
                storedValue.get(realValue);
                session.sendResponse(appendTs(Response.ok(realValue), ts));
            } else {
                session.sendResponse(appendTs(new Response(Response.NOT_FOUND, Response.EMPTY), ts));
            }
        }
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public void handlePut(@Param(value = "id", required = true) final String key,
                          @Param(value = "ack") final String ack,
                          @Param(value = "from") final String from,
                          final Request req,
                          final HttpSession session) throws IOException {
        if (key.isEmpty()) {
            session.sendResponse(getEmptyResponse(Response.BAD_REQUEST));
            return;
        }
        if (shouldBeForwarded(key, ack, from, req, session)) return;
        dao.upsert(new BaseEntry<>(fromString(key), MemorySegment.ofArray(prefixWrite(req.getBody()))));
        session.sendResponse(getEmptyResponse(Response.CREATED));
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public void handleDelete(@Param(value = "id", required = true) final String key,
                             @Param(value = "ack") final String ack,
                             @Param(value = "from") final String from,
                             final Request req,
                             final HttpSession session) throws IOException {
        if (key.isEmpty()) {
            session.sendResponse(getEmptyResponse(Response.BAD_REQUEST));
            return;
        }
        if (shouldBeForwarded(key, ack, from, req, session)) return;
        dao.upsert(new BaseEntry<>(fromString(key), MemorySegment.ofArray(prefixWrite(null))));
        session.sendResponse(getEmptyResponse(Response.ACCEPTED));
    }

    private boolean shouldBeForwarded(final String key,
                                      final String ack,
                                      final String from,
                                      final Request req,
                                      final HttpSession session) throws IOException {
        final int acks = toIntOrDefault(ack);
        final int froms = toIntOrDefault(from);
        if (acks < 1 || froms < 1 || acks > froms || froms > config.clusterUrls().size()) {
            session.sendResponse(getEmptyResponse(Response.BAD_REQUEST));
            return true;
        }
        if ((shards.getShard(key).url.equals(config.selfUrl()) && acks == 1 && froms == 1)
                || req.getHeader("Proxy: ") != null) {
            return false;
        }
        forwardRequestByKey(req, session, key, acks, froms);
        return true;
    }
}
