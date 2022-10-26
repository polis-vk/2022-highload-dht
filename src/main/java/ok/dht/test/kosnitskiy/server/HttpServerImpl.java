package ok.dht.test.kosnitskiy.server;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.kosnitskiy.dao.BaseEntry;
import ok.dht.test.kosnitskiy.dao.Config;
import ok.dht.test.kosnitskiy.dao.Entry;
import ok.dht.test.kosnitskiy.dao.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.UnexpectedException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class HttpServerImpl extends HttpServer {
    private static final int IN_MEMORY_SIZE = 8388608;

    private static final Logger LOG = LoggerFactory.getLogger(HttpServerImpl.class);
    private final ThreadPoolExecutor executor;
    private final MemorySegmentDao memorySegmentDao;
    private final MemorySegmentDao timestampDao;
    private final HttpClient httpClient;

    private final String serverUrl;
    private final Cluster thisCluster;
    private final List<Cluster> clusters;

    private final HashFunction hash = Hashing.murmur3_128();

    private static final long SHUTDOWN_WAIT_TIME_SECONDS = 60;

    private static final int MAX_THREADS_PER_NODE = Runtime.getRuntime().availableProcessors() / 2
            + (Runtime.getRuntime().availableProcessors() % 2 == 0 ? 0 : 1);
    private static final int MAX_TASKS_PER_NODE = MAX_THREADS_PER_NODE * 42;

    public HttpServerImpl(ServiceConfig config,
                          MemorySegmentDao memorySegmentDao,
                          ThreadPoolExecutor executor, Object... routers) throws IOException {
        super(createConfigFromPort(config.selfPort()), routers);
        this.executor = executor;
        this.memorySegmentDao = memorySegmentDao;

        Path path = Path.of(config.workingDir().toString() + "/timestamps");
        if (!Files.exists(path)) {
            boolean res = path.toFile().mkdirs();
            if (!res) {
                throw new UnexpectedException("Dir was not created");
            }
        }
        this.timestampDao = new MemorySegmentDao(new Config(path, IN_MEMORY_SIZE));

        this.httpClient = HttpClient.newHttpClient();
        serverUrl = config.selfUrl();
        clusters = config.clusterUrls().stream().map(Cluster::new).collect(Collectors.toList());
        Cluster thisCluster = null;
        for (Cluster cluster : clusters) {
            if (cluster.url.equals(serverUrl)) {
                thisCluster = cluster;
                break;
            }
        }
        if (thisCluster == null) {
            throw new UnexpectedException("Somehow cluster is not present within clusters");
        }
        this.thisCluster = thisCluster;
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        String id = request.getParameter("id=");
        if (!isTypeSupported(request)) {
            session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
            return;
        }

        if (!"/v0/entity".equals(request.getPath()) || id == null || id.isEmpty()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        String isSlave = request.getParameter("slave=");
        if (isSlave == null) {
            LOG.debug("I'm master + {}", serverUrl);
            masterHandle(id, request, session);
        } else {
            LOG.debug("I'm slave + {}", serverUrl);
            slaveHandle(id, request, session);
        }
    }

    private void slaveHandle(String id, Request request, HttpSession session) throws IOException {
        Cluster target = thisCluster;

        if (target.amountOfTasks.incrementAndGet() >= MAX_TASKS_PER_NODE) {
            target.amountOfTasks.decrementAndGet();
            session.sendResponse(new Response(Response.INTERNAL_ERROR, Response.EMPTY));
            return;
        }

        target.queue.add(() -> {
            try {
                session.sendResponse(handleSupported(request, id));
            } catch (Exception e) {
                try {
                    session.sendResponse(new Response(
                            Response.INTERNAL_ERROR,
                            ("Internal server error has occurred: " + e.getMessage()).getBytes(StandardCharsets.UTF_8)
                    ));
                } catch (IOException ex) {
                    LOG.error("Unable to send answer to " + session.getRemoteHost());
                    session.scheduleClose();
                }
            }
        });

        startWorker(id, target);
    }

    private void masterHandle(String id, Request request, HttpSession session) throws IOException {
        String nodesAmountStr = request.getParameter("from=");
        if (nodesAmountStr == null) {
            nodesAmountStr = Integer.toString(clusters.size());
        }
        String nodesAnswersRequiredStr = request.getParameter("ack=");
        if (nodesAnswersRequiredStr == null) {
            nodesAnswersRequiredStr = Integer.toString(clusters.size() / 2 + 1);
        }

        int nodesAmount;
        int nodesAnswersRequired;
        try {
            nodesAmount = Integer.parseInt(nodesAmountStr);
            nodesAnswersRequired = Integer.parseInt(nodesAnswersRequiredStr);
            if (nodesAnswersRequired > nodesAmount || nodesAmount < 1 || nodesAnswersRequired < 1) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        List<Cluster> targets = getTargetClustersFromKey(id, nodesAmount);

        AtomicInteger succeeded = new AtomicInteger(0);
        AtomicInteger tried = new AtomicInteger(0);

        PriorityBlockingQueue<ResponseVariant> variants = new PriorityBlockingQueue<ResponseVariant>();

        for (Cluster target : targets) {
            if (target.amountOfTasks.incrementAndGet() >= MAX_TASKS_PER_NODE) {
                target.amountOfTasks.decrementAndGet();
                session.sendResponse(new Response(Response.INTERNAL_ERROR, Response.EMPTY));
                LOG.error("Overflow of " + target.url);
                if (tried.incrementAndGet() == nodesAmount && succeeded.get() < nodesAnswersRequired) {
                    session.sendResponse(new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY));
                    return;
                }
                continue;
            }

            target.queue.add(() -> {
                try {
                    Response response =
                            target.url.equals(serverUrl) ? handleSupported(request, id) : proxyRequest(target, request);
                    if (isAffirmative(response)) {
                        long timestamp = 0;
                        if (response.getHeaders()[0].equals(Response.OK)
                                || response.getHeaders()[0].equals(Response.NOT_FOUND)) {
                            byte[] body = Response.EMPTY;

                            if (response.getBody() != Response.EMPTY) {
                                timestamp = bytesToLong(Arrays.copyOfRange(response.getBody(), 0, 8));
                                body = Arrays.copyOfRange(response.getBody(), 8, response.getBody().length);
                            }
                            response = new Response(response.getHeaders()[0], body);
                        }
                        variants.add(new ResponseVariant(response, timestamp));
                        LOG.debug("Got Affirmative " + serverUrl);
                        int succ = succeeded.incrementAndGet();
                        int tr = tried.incrementAndGet();
                        if (succ == nodesAnswersRequired) {
                            LOG.debug("Got required, sending response: "
                                    + " tried: " + tr + " succeeded: " + succ + " needed: "
                                    + nodesAnswersRequired + " total: " + nodesAmount);
                            session.sendResponse(variants.take().response);
                        } else if (tr == nodesAmount && succ < nodesAnswersRequired) {
                            LOG.debug("Failed to get quorum, sending response: "
                                    + " tried: " + tr + " succeeded: " + succ + " needed: "
                                    + nodesAnswersRequired + " total: " + nodesAmount);
                            session.sendResponse(new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY));
                        }
                    } else {
                        LOG.debug("Got Bad request " + serverUrl);
                        if (tried.incrementAndGet() == nodesAmount && succeeded.get() < nodesAnswersRequired) {
                            LOG.debug("Failed to get quorum, sending response: "
                                    + " tried: " + tried.get() + " succeeded: " + succeeded.get() + " needed: "
                                    + nodesAnswersRequired + " total: " + nodesAmount);
                            session.sendResponse(new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY));
                        }
                    }
                } catch (Exception e) {
                    try {
                        int tr = tried.incrementAndGet();
                        LOG.error("Internal server error has occurred: " + e.getMessage()
                                + " tried: " + tr + " succeeded: " + succeeded.get() + " needed: "
                                + nodesAnswersRequired + " total: " + nodesAmount);
                        if (tr == nodesAmount && succeeded.get() < nodesAnswersRequired) {
                            session.sendResponse(new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY));
                        }
                    } catch (IOException ex) {
                        LOG.error("Unable to send answer to " + session.getRemoteHost());
                        session.scheduleClose();
                    }
                }
            });

            startWorker(id, target);
        }
    }

    @Override
    public synchronized void stop() {
        executor.shutdown();
        boolean isTerminated;
        try {
            isTerminated = executor.awaitTermination(SHUTDOWN_WAIT_TIME_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            isTerminated = false;
            LOG.error("Waiting for tasks to finish timed out");
            Thread.currentThread().interrupt();
        }
        if (!isTerminated) {
            try {
                executor.shutdownNow();
            } catch (Exception exception) {
                LOG.error("Server won't shutdown");
                System.exit(1);
            }
        }
        for (SelectorThread selectorThread : selectors) {
            for (Session session : selectorThread.selector) {
                session.scheduleClose();
            }
        }
        try {
            timestampDao.close();
        } catch (IOException e) {
            LOG.error("Failed to close dao");
        }
        super.stop();
    }

    private void startWorker(String id, Cluster target) {
        if (target.amountOfWorkers.incrementAndGet() <= MAX_THREADS_PER_NODE) {
            executor.execute(() -> {
                Runnable task;
                while (true) {
                    task = target.queue.poll();
                    if (task == null) {
                        target.amountOfWorkers.decrementAndGet();
                        break;
                    }
                    LOG.debug("Url {} for id {} (my url is {})", target.url, id, serverUrl);
                    try {
                        task.run();
                    } catch (Exception e) {
                        LOG.error("Error while executing task");
                    } finally {
                        target.amountOfTasks.decrementAndGet();
                    }
                }
            });
        } else {
            target.amountOfWorkers.decrementAndGet();
        }
    }

    private boolean isTypeSupported(Request request) {
        return request.getMethod() == Request.METHOD_GET
                || request.getMethod() == Request.METHOD_PUT
                || request.getMethod() == Request.METHOD_DELETE;
    }

    private boolean isAffirmative(Response response) {
        switch (response.getHeaders()[0]) {
            case Response.NOT_FOUND, Response.ACCEPTED, Response.CREATED, Response.OK -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private Response proxyRequest(Cluster target, Request request) throws IOException, InterruptedException {
        LOG.debug("Sending request to slave " + serverUrl);
        HttpRequest proxyRequest = HttpRequest.newBuilder(URI.create(target.url + request.getURI() + "&slave=true"))
                .method(
                        request.getMethodName(),
                        HttpRequest
                                .BodyPublishers
                                .ofByteArray(request.getBody() == null ? Response.EMPTY : request.getBody())
                )
                .timeout(Duration.ofMillis(100))
                .build();
        HttpResponse<byte[]> response = httpClient.send(proxyRequest,
                HttpResponse.BodyHandlers.ofByteArray());

        return new Response(convertResponse(response.statusCode()), response.body());
    }

    private Response handleSupported(Request request, String id) throws IOException {
        LOG.debug("Handling myself " + serverUrl);
        switch (request.getMethod()) {
            case Request.METHOD_GET -> {
                Entry<MemorySegment> entry;
                entry = memorySegmentDao.get(MemorySegment.ofArray(id.toCharArray()));
                Entry<MemorySegment> time = timestampDao.get(MemorySegment.ofArray(id.toCharArray()));
                if (entry != null) {
                    return new Response(
                            Response.OK,
                            addAll(time.value().toByteArray(), entry.value().toByteArray())
                    );
                }
                if (time != null) {
                    return new Response(Response.NOT_FOUND, time.value().toByteArray());
                }
                return new Response(Response.NOT_FOUND, Response.EMPTY);
            }
            case Request.METHOD_PUT -> {
                memorySegmentDao.upsert(new BaseEntry<>(MemorySegment.ofArray(id.toCharArray()),
                        MemorySegment.ofArray(request.getBody())));
                timestampDao.upsert(new BaseEntry<>(MemorySegment.ofArray(id.toCharArray()),
                        MemorySegment.ofArray(longToBytes(System.currentTimeMillis()))));
                return new Response(Response.CREATED, Response.EMPTY);
            }
            case Request.METHOD_DELETE -> {
                memorySegmentDao.upsert(new BaseEntry<>(MemorySegment.ofArray(id.toCharArray()), null));
                timestampDao.upsert(new BaseEntry<>(MemorySegment.ofArray(id.toCharArray()),
                        MemorySegment.ofArray(longToBytes(System.currentTimeMillis()))));
                return new Response(Response.ACCEPTED, Response.EMPTY);
            }
            default -> {
                throw new UnsupportedOperationException("Unsupported request received");
            }
        }
    }

    private List<Cluster> getTargetClustersFromKey(String key, int size) {
        int starting = hash.newHasher().putString(key, StandardCharsets.UTF_8).hash().hashCode() & 0x7FFFFFFF
                % clusters.size();

        int last = starting + size;

        List<Cluster> targets = new ArrayList<>(size);

        if (last <= clusters.size()) {
            for (int i = starting; i < last; i++) {
                targets.add(clusters.get(i));
            }
        } else {
            for (int i = starting; i < clusters.size(); i++) {
                targets.add(clusters.get(i));
            }
            for (int i = 0; i < last - clusters.size(); i++) {
                targets.add(clusters.get(i));
            }
        }

        return targets;
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    private static String convertResponse(int statusCode) {
        return switch (statusCode) {
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
            default -> throw new IllegalArgumentException("Unknown status code: " + statusCode);
        };
    }

    private static class Cluster {
        public final String url;

        public final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<Runnable>();

        public final AtomicInteger amountOfTasks;
        public final AtomicInteger amountOfWorkers;

        public Cluster(String url) {
            this.url = url;
            this.amountOfTasks = new AtomicInteger(0);
            this.amountOfWorkers = new AtomicInteger(0);
        }
    }

    private static class ResponseVariant implements Comparable<ResponseVariant> {
        public final Response response;
        public final long timestamp;

        public ResponseVariant(Response response, long timestamp) {
            this.response = response;
            this.timestamp = timestamp;
        }

        @Override
        public int compareTo(ResponseVariant o) {
            return Long.compare(o.timestamp, this.timestamp);
        }
    }

    public static byte[] addAll(final byte[] array1, byte[] array2) {
        byte[] joinedArray = Arrays.copyOf(array1, array1.length + array2.length);
        System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
        return joinedArray;
    }

    public static byte[] longToBytes(long l) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte)(l & 0xFF);
            l >>= 8;
        }
        return result;
    }

    public static long bytesToLong(final byte[] b) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result <<= 8;
            result |= (b[i] & 0xFF);
        }
        return result;
    }

}
