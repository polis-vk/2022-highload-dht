package ok.dht.test.yasevich;

import ok.dht.test.yasevich.TimeStampingDao.TimeStampedValue;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ReplicasManager {
    public static final String NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";

    private final TimeStampingDao dao;
    private final RandevouzHashingRouter shardingRouter;
    private final String selfUrl;
    private final Executor executor;

    public ReplicasManager(TimeStampingDao dao, RandevouzHashingRouter shardingRouter, String selfUrl, Executor executor) {
        this.dao = dao;
        this.shardingRouter = shardingRouter;
        this.selfUrl = selfUrl;
        this.executor = executor;
    }

    void handleReplicatingRequest(HttpSession session, Request request, String key, int ack, int from) {
        Queue<RandevouzHashingRouter.Node> responsibleNodes = shardingRouter.responsibleNodes(key, from);
        if (responsibleNodes.isEmpty()) {
            return;
        }
        switch (request.getMethod()) {
            case Request.METHOD_PUT ->
                    handleUpsert(session, request, key, responsibleNodes, ack, from, Response.CREATED);
            case Request.METHOD_DELETE ->
                    handleUpsert(session, request, key, responsibleNodes, ack, from, Response.ACCEPTED);
            case Request.METHOD_GET -> handleGet(session, request, key, responsibleNodes, ack, from);
            default -> sendResponse(session, new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
        }
    }

    private void handleUpsert(
            HttpSession session, Request request,
            String key,
            Iterable<RandevouzHashingRouter.Node> responsibleNodes,
            int ack, int from,
            String okMessage
    ) {
        AtomicInteger oks = new AtomicInteger();
        AtomicInteger responsesTotal = new AtomicInteger();
        AtomicBoolean alreadyResponded = new AtomicBoolean();

        for (RandevouzHashingRouter.Node node : responsibleNodes) {
            if (node.url.equals(selfUrl)) {
                try {
                    doUpsert(request, key);
                    probablyResponseGood(session, goodResponseForUpsert(okMessage), ack, oks,
                            responsesTotal, alreadyResponded);
                } catch (Exception e) {
                    handleNodeFailure(e, node, request, key);
                    probablyResponseBad(session, from, responsesTotal, alreadyResponded);
                }
                continue;
            }
            shardingRouter.routedRequestFuture(request, key, node)
                    .orTimeout(ServiceImpl.ROUTED_REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .whenCompleteAsync((httpResponse, throwable) -> {
                        if (throwable != null) {
                            probablyResponseBad(session, from, responsesTotal, alreadyResponded);
                            handleNodeFailure(throwable, node, request, key);
                            return;
                        }
                        if (httpResponse.statusCode() == Integer.parseInt(okMessage.substring(0, 3))) {
                            probablyResponseGood(session, goodResponseForUpsert(okMessage), ack,
                                    oks, responsesTotal, alreadyResponded);
                        } else {
                            probablyResponseBad(session, from, responsesTotal, alreadyResponded);
                        }
                    }, executor);
        }
    }

    private void handleGet(
            HttpSession session, Request request,
            String key,
            Iterable<RandevouzHashingRouter.Node> responsibleNodes,
            int ack, int from
    ) {
        AtomicInteger nonFailed = new AtomicInteger();
        AtomicInteger responsesTotal = new AtomicInteger();
        AtomicBoolean alreadyResponded = new AtomicBoolean();
        TimeStampedValue[] latestValueRef = new TimeStampedValue[1];
        Lock lock = new ReentrantLock();
        for (RandevouzHashingRouter.Node node : responsibleNodes) {
            if (node.url.equals(selfUrl)) {
                try {
                    TimeStampedValue value = dao.get(key);
                    value = updateValueIfNeeded(latestValueRef, lock, value);
                    probablyResponseGoodToGet(session, ack, value, nonFailed, responsesTotal, alreadyResponded);
                } catch (Exception e) {
                    handleNodeFailure(e, node, request, key);
                    probablyResponseBad(session, from, responsesTotal, alreadyResponded);
                }
                continue;
            }
            shardingRouter.routedRequestFuture(request, key, node)
                    .orTimeout(ServiceImpl.ROUTED_REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .whenCompleteAsync((httpResponse, throwable) -> {
                        if (throwable != null) {
                            handleNodeFailure(throwable, node, request, key);
                            probablyResponseBad(session, from, responsesTotal, alreadyResponded);
                            return;
                        }
                        if (httpResponse.statusCode() != 200 && httpResponse.statusCode() != 404) {
                            probablyResponseBad(session, from, responsesTotal, alreadyResponded);
                            return;
                        }
                        TimeStampedValue value = updateValueIfNeeded(latestValueRef, lock, httpResponse.body(),
                                httpResponse.statusCode());
                        probablyResponseGoodToGet(session, ack, value, nonFailed, responsesTotal, alreadyResponded);
                    }, executor);
        }
    }

    private TimeStampedValue updateValueIfNeeded(
            TimeStampedValue[] latestValueRef,
            Lock lock,
            byte[] body,
            int statusCode
    ) {
        lock.lock();
        try {
            TimeStampedValue value;
            if (statusCode == 404) {
                if (body.length == 0) {
                    return latestValueRef[0];
                }
                value = TimeStampedValue.tombstoneFromTime(body);
            } else {
                value = TimeStampedValue.fromBytes(body);
            }
            if (latestValueRef[0] == null || value.time > latestValueRef[0].time) {
                latestValueRef[0] = value;
            }
            return latestValueRef[0];
        } finally {
            lock.unlock();
        }
    }

    private TimeStampedValue updateValueIfNeeded(TimeStampedValue[] latestValueRef, Lock lock, TimeStampedValue value) {
        lock.lock();
        try {
            if (latestValueRef[0] == null || value.time > latestValueRef[0].time) {
                latestValueRef[0] = value;
            }
            return latestValueRef[0];
        } finally {
            lock.unlock();
        }
    }

    private static void probablyResponseGood(
            HttpSession session,
            Response goodResponse,
            int ack,
            AtomicInteger okResponses,
            AtomicInteger totalResponses,
            AtomicBoolean alreadyResponded
    ) {
        if (okResponses.incrementAndGet() == ack) {
            sendResponse(session, goodResponse);
            alreadyResponded.set(true);
        }
        totalResponses.incrementAndGet();
    }

    private static void probablyResponseGoodToGet(
            HttpSession session,
            int ack,
            TimeStampedValue latestValue,
            AtomicInteger nonFailedResponses,
            AtomicInteger totalResponses,
            AtomicBoolean alreadyResponded
    ) {
        totalResponses.incrementAndGet();
        if (nonFailedResponses.incrementAndGet() != ack) {
            return;
        }
        if (latestValue == null || latestValue.value == null) {
            sendResponse(session, new Response(Response.NOT_FOUND, Response.EMPTY));
        } else {
            sendResponse(session, new Response(Response.OK, latestValue.valueBytes()));
        }
        alreadyResponded.set(true);
    }

    private static void probablyResponseBad(
            HttpSession session,
            int from,
            AtomicInteger totalResponses,
            AtomicBoolean alreadyResponded
    ) {
        if (totalResponses.incrementAndGet() == from) {
            if (!alreadyResponded.get()) {
                sendResponse(session, new Response(NOT_ENOUGH_REPLICAS, Response.EMPTY));
            }
        }
    }

    private static Response goodResponseForUpsert(String okMessage) {
        return new Response(okMessage, Response.EMPTY);
    }

    private static void handleNodeFailure(Throwable t, RandevouzHashingRouter.Node node, Request request, String key) {
        node.managePossibleIllness();
        ServiceImpl.LOGGER.error(t + " when requesting a " + node.url + " to respond to an "
                + request.getMethod() + " method for " + key);
    }

    private void doUpsert(Request request, String key) {
        switch (request.getMethod()) {
            case Request.METHOD_PUT -> dao.upsertTimeStamped(key, request.getBody());
            case Request.METHOD_DELETE -> dao.upsertTimeStamped(key, null);
            default -> throw new IllegalArgumentException();
        }
    }

    static void sendResponse(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e) {
            ServiceImpl.LOGGER.error("Error when sending " + response.getStatus());
            session.close();
        }
    }

}
