package ok.dht.test.siniachenko.service;

import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.siniachenko.TycoonHttpServer;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TycoonService implements ok.dht.Service, Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(TycoonService.class);
    private static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();
    public static final String PATH = "/v0/entity";
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

        // Http Client
        httpClient = HttpClient.newHttpClient();

        // Http Server
        server = new TycoonHttpServer(this, config.selfPort());
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

    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (!PATH.equals(request.getPath())) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        // TODO: differ replicas request and inner request !!!!
        String idParameter = request.getParameter("id=");
        if (idParameter == null || idParameter.isEmpty()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

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
            } catch (NumberFormatException e) {
                session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                return;
            }
        }

        if (Request.METHOD_GET == request.getMethod()) {
            // TODO: should do almost all process request in executor thread ???
            int[] nodeUrls = nodeMapper.getNodeUrlsByKey(Utf8.toBytes(idParameter));

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
                    proxyRequest(session, request, idParameter, nodeUrlByKey).thenAccept(
                        response -> {
                            int statusCode = response.statusCode();
                            if (statusCode != 404 && statusCode != 410 && statusCode != 200) {
                                LOG.error(
                                    "Unexpected status {} code requesting node {}", statusCode, nodeUrlByKey
                                );
                                checkAllFinishedOrFailed(session, ack, from, ackReceivedRef, finishedOrFailedRef);
                                return;
                            }

                            try {
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
            if (needLocalWork) {
                try {
                    byte[] value = get(idParameter);
                    int statusCode;
                    if (value == null) {
                        statusCode = 404;
                    } else {
                        boolean deleted = readFlagDeletedFromBytes(value);
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
    }

    private void checkAllFinishedOrFailed(
        HttpSession session, int ack, int from, AtomicInteger ackReceivedRef, AtomicInteger finishedOrFailedRef
    ) {
        int finishedOrFailed = finishedOrFailedRef.incrementAndGet();
        if (finishedOrFailed == from) {
            if (ackReceivedRef.get() < ack) {
                sendResponse(
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
                    long timeMillis = readTimeMillisFromBytes(bodies[replicaAnswered]);
                    if (maxTimeMillis < timeMillis) {
                        maxTimeMillis = timeMillis;
                        maxTimeStampReplica = replicaAnswered;
                    }
                }
            }
            if (maxTimeStampReplica == -1 || statusCodes[maxTimeStampReplica] == 410) {
                sendResponse(session, new Response(Response.NOT_FOUND, Response.EMPTY));
            } else {
                byte[] realValue = readValueFromBytes(bodies[maxTimeStampReplica]);
                sendResponse(session, new Response(Response.OK, realValue));
            }
        }
    }

    private byte[] get(String id) throws DBException {
        return levelDb.get(Utf8.toBytes(id));
    }


    private void executeLocal(HttpSession session, Request request, String id) {
        try {
            executorService.execute(() -> {
                Response response = switch (request.getMethod()) {
                    case Request.METHOD_GET -> null;
                    case Request.METHOD_PUT -> upsert(id, request.getBody());
                    case Request.METHOD_DELETE -> delete(id);
                    default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
                };
                sendResponse(session, response);
            });
        } catch (RejectedExecutionException e) {
            LOG.error("Cannot execute task", e);
            sendResponse(session, new Response(Response.INTERNAL_ERROR, Response.EMPTY));
        }
    }

    private CompletableFuture<HttpResponse<byte[]>> proxyRequest(HttpSession session, Request request, String idParameter, String nodeUrl) {
        Integer timeout = nodeRequestsTimeouts.computeIfAbsent(nodeUrl, s -> DEFAULT_TIMEOUT_MILLIS);
        return httpClient.sendAsync(
                HttpRequest.newBuilder()
                    .uri(URI.create(nodeUrl + PATH + "?id=" + idParameter))
                    .method(request.getMethodName(), HttpRequest.BodyPublishers.ofByteArray(request.getBody()))
                    .build(),
                HttpResponse.BodyHandlers.ofByteArray()
            ).orTimeout(timeout, TimeUnit.MILLISECONDS);
    }

    private void sendResponse(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e1) {
            LOG.error("I/O error while sending response", e1);
            try {
                session.close();
            } catch (Exception e2) {
                e2.addSuppressed(e1);
                LOG.error("Exception while closing session", e2);
            }
        }
    }

    private Response upsert(String id, byte[] value) {
        try {
            levelDb.put(Utf8.toBytes(id), value);
            return new Response(Response.CREATED, Response.EMPTY);
        } catch (DBException e) {
            LOG.error("Error in DB", e);
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    private Response delete(String id) {
        try {
            levelDb.delete(Utf8.toBytes(id));
            return new Response(Response.ACCEPTED, Response.EMPTY);
        } catch (DBException e) {
            LOG.error("Error in DB", e);
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    private byte[] withCurrentTimestampAndNotDeletedFlag(byte[] value, boolean flagDeleted) {
        byte[] newValue = new byte[value.length + 9];
        if (flagDeleted) {
            newValue[0] = 0b0;
        } else {
            newValue[0] = 0b1;
        }
        System.arraycopy(value, 0, newValue, 1, value.length);
        long currentTimeMillis = System.currentTimeMillis();
        for (int i = 0; i < 8; ++i) {
            newValue[value.length + 1 + i] = (byte) (currentTimeMillis << (8 * i));
        }
        return newValue;
    }

    private boolean readFlagDeletedFromBytes(byte[] value) {
        return value[0] == 0b0;
    }

    private long readTimeMillisFromBytes(byte[] value) {
        long timeMillisFromLastBytes = 0;
        for (int i = 0; i < 8; ++i) {
            timeMillisFromLastBytes += ((long) value[value.length - 8 + i]) >> (8 * i);
        }
        return timeMillisFromLastBytes;
    }

    private byte[] readValueFromBytes(byte[] codedValue) {
        byte[] value = new byte[codedValue.length - 9];
        System.arraycopy(codedValue, 1, value, 0, value.length);
        return value;
    }

    @ServiceFactory(stage = 3, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public ok.dht.Service create(ServiceConfig config) {
            return new TycoonService(config);
        }
    }
}
