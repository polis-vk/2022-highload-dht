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
    private final int defaultFromCount;
    private final int defaultAckCount;
    private DB levelDb;
    private TycoonHttpServer server;
    private ExecutorService executorService;
    private HttpClient httpClient;

    public TycoonService(ServiceConfig config) {
        this.config = config;
        this.nodeMapper = new NodeMapper(config.clusterUrls());
        this.defaultFromCount = config.clusterUrls().size();
        this.defaultAckCount = (config.clusterUrls().size() + 1) / 2;
        this.nodeRequestsTimeouts = new ConcurrentHashMap<>();
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
        final int ack, from;
        String replicasParameter = request.getParameter("replicas=");
        if (replicasParameter == null || replicasParameter.isEmpty()) {
            from = defaultFromCount;
            ack = defaultAckCount;
        } else {
            int sepIndex = replicasParameter.indexOf("/");
            if (sepIndex == -1) {
                session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                return;
            }
            String fromString = replicasParameter.substring(0, sepIndex);
            String ackString = replicasParameter.substring(sepIndex + 1);
            try {
                from = Integer.parseInt(fromString);
                ack = Integer.parseInt(ackString);
                if (!(
                    0 < from && from <= config.clusterUrls().size()
                        && 0 < ack && ack <= config.clusterUrls().size()
                        && ack <= from
                )) {
                    session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                    return;
                }
            } catch (NumberFormatException e) {
                session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                return;
            }
        }

        // TODO: should do almost all process request in executor thread ???
        int[] nodeUrls = nodeMapper.getNodeUrlsByKey(Utf8.toBytes(id));

        int[] statusCodes = new int[ack];
        byte[][] bodies = new byte[ack][];
        AtomicInteger ackReceivedRef = new AtomicInteger();
        AtomicInteger finishedOrFailedRef = new AtomicInteger();

        boolean needLocalWork = false;
        for (int replicaToSend = 0; replicaToSend < from; ++replicaToSend) {
            String nodeUrlByKey = nodeMapper.getNodeUrls().get(nodeUrls[replicaToSend]);
            if (config.selfUrl().equals(nodeUrlByKey)) {
                needLocalWork = true;
            } else {
                proxyRequest(request, id, nodeUrlByKey).thenAccept(
                    response -> {
                        int statusCode = response.statusCode();
                        // TODO: different logic for different methods
                        if (statusCode != 404 && statusCode != 410 && statusCode != 200) {
                            LOG.error(
                                "Unexpected status {} code requesting node {}", statusCode, nodeUrlByKey
                            );
                            checkAllFinishedOrFailed(session, ack, from, ackReceivedRef, finishedOrFailedRef);
                            return;
                        }

                        try {
                            // TODO: different aggregate methods
                            addResultAndAggregateIfNeed(
                                session, ack, statusCodes, bodies, ackReceivedRef, response.body(), statusCode
                            );
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
        // different for different methods
        if (needLocalWork) {
            try {
                byte[] value = levelDb.get(Utf8.toBytes(id));
                int statusCode;
                if (value == null) {
                    statusCode = 404;
                } else {
                    boolean deleted = Utils.readFlagDeletedFromBytes(value);
                    if (deleted) {
                        statusCode = 410;
                    } else {
                        statusCode = 200;
                    }
                }
                addResultAndAggregateIfNeed(session, ack, statusCodes, bodies, ackReceivedRef, value, statusCode);
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

    private void addResultAndAggregateIfNeed(
        HttpSession session, int ack, int[] statusCodes, byte[][] bodies,
        AtomicInteger ackReceivedRef, byte[] value, int statusCode
    ) {
        // saving received body and status code
        int ackReceived;
        while (true) {
            ackReceived = ackReceivedRef.get();
            if (ackReceived >= ack) {
                return;
            }
            if (ackReceivedRef.compareAndSet(ackReceived, ackReceived + 1)) {
                bodies[ackReceived] = value;
                statusCodes[ackReceived] = statusCode;
                break;
            }
        }

        // aggregating results after receiving enough replicas
        if (ackReceived + 1 == ack) {
            int maxTimeStampReplica = -1;
            long maxTimeMillis = 0;
            for (int replicaAnswered = 0; replicaAnswered < ack; replicaAnswered++) {
                if (statusCodes[replicaAnswered] != 404) {
                    long timeMillis = Utils.readTimeMillisFromBytes(bodies[replicaAnswered]);
                    if (maxTimeMillis < timeMillis) {
                        maxTimeMillis = timeMillis;
                        maxTimeStampReplica = replicaAnswered;
                    }
                }
            }
            if (maxTimeStampReplica == -1 || statusCodes[maxTimeStampReplica] == 410) {
                TycoonHttpServer.sendResponse(session, new Response(Response.NOT_FOUND, Response.EMPTY));
            } else {
                byte[] realValue = Utils.readValueFromBytes(bodies[maxTimeStampReplica]);
                TycoonHttpServer.sendResponse(session, new Response(Response.OK, realValue));
            }
        }
    }

    private CompletableFuture<HttpResponse<byte[]>> proxyRequest(Request request, String idParameter, String nodeUrl) {
        Integer timeout = nodeRequestsTimeouts.computeIfAbsent(nodeUrl, s -> DEFAULT_TIMEOUT_MILLIS);
        return httpClient.sendAsync(
            HttpRequest.newBuilder()
                .uri(URI.create(nodeUrl + TycoonHttpServer.PATH + "?id=" + idParameter))
                .method(request.getMethodName(), HttpRequest.BodyPublishers.ofByteArray(request.getBody()))
                .timeout(Duration.ofMillis(timeout))
                .header(TycoonHttpServer.REQUEST_TO_REPLICA_HEADER, "")
                .build(),
            HttpResponse.BodyHandlers.ofByteArray()
        );
    }

    @ServiceFactory(stage = 3, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public ok.dht.Service create(ServiceConfig config) {
            return new TycoonService(config);
        }
    }
}
