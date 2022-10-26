package ok.dht.test.siniachenko.service;

import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.siniachenko.TycoonHttpServer;
import ok.dht.test.siniachenko.Utils;
import ok.dht.test.siniachenko.rendezvoushashing.NodeMapper;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.util.Utf8;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.DbImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TycoonService implements ok.dht.Service, Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(TycoonService.class);
    private static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();
    public static final int THREAD_POOL_QUEUE_CAPACITY = 128;
    public static final int DEFAULT_TIMEOUT_MILLIS = 1000;
    public static final int MIN_TIMEOUT_MILLIS = 300;
    public static final int MAX_TIMEOUT_MILLIS = 2000;
    public static final String NOT_ENOUGH_REPLICAS_RESULT_CODE = "504 Not Enough Replicas";

    private final ServiceConfig config;
    private final NodeMapper nodeMapper;
    private final ConcurrentMap<String, Integer> nodeRequestsTimeouts;
    private final ReplicatedRequestAggregator replicatedRequestAggregator;
    private final int defaultFromCount;
    private final int defaultAckCount;
    private DB levelDb;
    private TycoonHttpServer server;
    private ExecutorService executorService;
    private HttpClient httpClient;

    public TycoonService(ServiceConfig config) {
        this.config = config;
        this.nodeMapper = new NodeMapper(config.clusterUrls());
        this.nodeRequestsTimeouts = new ConcurrentHashMap<>();
        this.replicatedRequestAggregator = new ReplicatedRequestAggregator();
        this.defaultFromCount = config.clusterUrls().size();
        this.defaultAckCount = config.clusterUrls().size() / 2 + 1;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        // DB
        levelDb = new DbImpl(new Options(), config.workingDir().toFile());
        LOG.info("Started DB in directory {}", config.workingDir());

        // Executor
        int threadPoolSize = AVAILABLE_PROCESSORS;
        executorService = new ThreadPoolExecutor(
            threadPoolSize, threadPoolSize,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(THREAD_POOL_QUEUE_CAPACITY)
        );

        // Replica
        EntityServiceReplica entityServiceReplica = new EntityServiceReplica(levelDb);

        // Http Client
        httpClient = HttpClient.newHttpClient();

        // Http Server
        server = new TycoonHttpServer(
            config.selfPort(),
            this,
            entityServiceReplica
        );
        server.start();
        LOG.info("Service started on {}, executor threads: {}", config.selfUrl(), threadPoolSize);

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        if (server != null && !server.isClosed()) {
            server.stop();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
        if (levelDb != null) {
            levelDb.close();
        }

        return CompletableFuture.completedFuture(null);
    }

    public void executeRequest(Request request, HttpSession session, String id) throws IOException {
        final int ack;
        final int from;
        String fromParameter = request.getParameter("from=");
        try {
            if (fromParameter == null || fromParameter.isEmpty()) {
                from = defaultFromCount;
            } else {
                from = Integer.parseInt(fromParameter);
            }
            String ackParameter = request.getParameter("ack=");
            if (ackParameter == null || ackParameter.isEmpty()) {
                ack = defaultAckCount;
            } else {
                ack = Integer.parseInt(ackParameter);
            }
        } catch (NumberFormatException e) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }
        if (!(
            0 < from && from <= config.clusterUrls().size()
                && 0 < ack && ack <= config.clusterUrls().size()
                && ack <= from
        )) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        if (request.getMethod() == Request.METHOD_PUT) {
            request.setBody(Utils.withCurrentTimestampAndFlagDeleted(request.getBody(), false));
        } else if (request.getMethod() == Request.METHOD_DELETE) {
            request.setBody(Utils.withCurrentTimestampAndFlagDeleted(new byte[0], true));
        }

        // TODO: should do almost all process request in executor thread ???
        long[] nodeUrls = nodeMapper.getNodeUrlsByKey(Utf8.toBytes(id));

        byte[][] bodies = new byte[ack][];
        AtomicInteger ackReceivedRef = new AtomicInteger();
        AtomicInteger finishedOrFailedRef = new AtomicInteger();

        boolean needLocalWork = false;
        for (int replicaToSend = 0; replicaToSend < from; ++replicaToSend) {
            String nodeUrlByKey = nodeMapper.getNodeUrls().get(
                (int) (nodeUrls[replicaToSend] >> (long) 32)
            );
            if (config.selfUrl().equals(nodeUrlByKey)) {
                needLocalWork = true;
            } else {
                proxyRequest(request, id, nodeUrlByKey).thenAccept(
                    response -> {
                        try {
                            if (replicatedRequestAggregator.filterSuccessfulStatusCode(
                                request.getMethod(), response.statusCode()
                            )) {
                                Response responseGot = replicatedRequestAggregator.addResultAndAggregateIfNeed(
                                    ack, bodies, ackReceivedRef, response.body(), request.getMethod()
                                );
                                if (responseGot != null) {
                                    TycoonHttpServer.sendResponse(session, responseGot);
                                }
                            }
                        } finally {
                            checkAllFinishedOrFailed(session, ack, from, ackReceivedRef, finishedOrFailedRef);
                        }
                    }
                ).exceptionally(ex -> {
                    nodeRequestsTimeouts.compute(nodeUrlByKey, (url, t) -> Math.min(t / 2, MAX_TIMEOUT_MILLIS));
                    LOG.error("Error after proxy request to {}", nodeUrlByKey, ex);
                    checkAllFinishedOrFailed(session, ack, from, ackReceivedRef, finishedOrFailedRef);
                    return null;
                });
            }
        }
        // TODO: different for different methods
        if (needLocalWork) {
            try {
                byte[] value = null;
                switch (request.getMethod()) {
                    case Request.METHOD_GET -> value = levelDb.get(Utf8.toBytes(id));
                    case Request.METHOD_PUT -> levelDb.put(Utf8.toBytes(id), request.getBody());
                    case Request.METHOD_DELETE -> levelDb.put(Utf8.toBytes(id), request.getBody());
                    default -> {
                        return;
                    }
                }
                Response responseGot = replicatedRequestAggregator.addResultAndAggregateIfNeed(
                    ack, bodies, ackReceivedRef, value, request.getMethod()
                );
                if (responseGot != null) {
                    TycoonHttpServer.sendResponse(session, responseGot);
                }
            } catch (DBException e) {
                LOG.error("Error in DB", e);
            } finally {
                checkAllFinishedOrFailed(session, ack, from, ackReceivedRef, finishedOrFailedRef);
            }
        }
    }

    private void checkAllFinishedOrFailed(
        HttpSession session, int ack, int from, AtomicInteger ackReceivedRef, AtomicInteger finishedOrFailedRef
    ) {
        int finishedOrFailed = finishedOrFailedRef.incrementAndGet();
        if (finishedOrFailed == from) {
            if (ackReceivedRef.get() < ack) {
                TycoonHttpServer.sendResponse(
                    session, new Response(NOT_ENOUGH_REPLICAS_RESULT_CODE, Response.EMPTY)
                );
            }
        }
    }

    private CompletableFuture<HttpResponse<byte[]>> proxyRequest(Request request, String idParameter, String nodeUrl) {
        Integer timeout = nodeRequestsTimeouts.computeIfAbsent(nodeUrl, s -> DEFAULT_TIMEOUT_MILLIS);
        System.out.println(Arrays.toString(request.getBody()));
        return httpClient.sendAsync(
            HttpRequest.newBuilder()
                .uri(URI.create(nodeUrl + TycoonHttpServer.PATH + "?id=" + idParameter))
                .method(request.getMethodName(),
                    request.getBody() == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(request.getBody()))
                .timeout(Duration.ofMillis(timeout))
                .header(TycoonHttpServer.REQUEST_TO_REPLICA_HEADER, "")
                .build(),
            HttpResponse.BodyHandlers.ofByteArray()
        );
    }

    @ServiceFactory(stage = 4, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public ok.dht.Service create(ServiceConfig config) {
            return new TycoonService(config);
        }
    }
}
