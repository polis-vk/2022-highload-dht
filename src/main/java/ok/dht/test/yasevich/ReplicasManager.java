package ok.dht.test.yasevich;

import ok.dht.test.yasevich.TimeStampingDao.TimeStampedValue;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

import java.net.http.HttpResponse;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ReplicasManager {
    public static final String NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";

    private final TimeStampingDao dao;
    private final RandevouzHashingRouter shardingRouter;
    private final String selfUrl;

    public ReplicasManager(
            TimeStampingDao dao,
            RandevouzHashingRouter shardingRouter,
            String selfUrl
    ) {
        this.dao = dao;
        this.shardingRouter = shardingRouter;
        this.selfUrl = selfUrl;
    }

    void handleReplicatingRequest(HttpSession session, Request request, String key, int ack, int from) {
        Queue<RandevouzHashingRouter.Node> responsibleNodes = shardingRouter.responsibleNodes(key, from);
        if (responsibleNodes.isEmpty()) {
            return;
        }
        switch (request.getMethod()) {
            case Request.METHOD_PUT ->
                    handleUpsert(session, request, key, responsibleNodes, ack, from, Response.CREATED, 201);
            case Request.METHOD_DELETE ->
                    handleUpsert(session, request, key, responsibleNodes, ack, from, Response.ACCEPTED, 202);
            case Request.METHOD_GET -> handleGet(session, request, key, responsibleNodes, ack, from);
            default -> ServiceImpl.sendResponse(session, new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
        }
    }

    private void handleUpsert(
            HttpSession session, Request request,
            String key,
            Iterable<RandevouzHashingRouter.Node> responsibleNodes,
            int ack, int from,
            String okMessage, int okStatusCode
    ) {
        ReplicatingResponseCounter counter = new ReplicatingResponseCounter(ack, from);

        Consumer<HttpResponse<byte[]>> consumer = (httpResponse) -> {
            if (httpResponse.statusCode() == okStatusCode) {
                responseToUpsertIfNeeded(session, okMessage, counter);
            } else {
                counter.responseFailureIfNeeded(session);
            }
        };

        Runnable daoWork = () -> {
            doUpsert(request, key);
            responseToUpsertIfNeeded(session, okMessage, counter);
        };

        handle(session, request, key, responsibleNodes, counter, consumer, daoWork);
    }

    private void doUpsert(Request request, String key) {
        switch (request.getMethod()) {
            case Request.METHOD_PUT -> dao.upsertTimeStamped(key, request.getBody());
            case Request.METHOD_DELETE -> dao.upsertTimeStamped(key, null);
            default -> throw new IllegalArgumentException();
        }
    }

    private void handleGet(
            HttpSession session, Request request,
            String key,
            Iterable<RandevouzHashingRouter.Node> responsibleNodes,
            int ack, int from
    ) {
        ReplicatingGetAggregator aggregator = new ReplicatingGetAggregator(ack, from);

        Consumer<HttpResponse<byte[]>> consumer = (httpResponse) -> {
            TimeStampedValue value = aggregator.updateValueIfNeeded(valueFromResponse(httpResponse));
            responseToGetIfNeeded(session, aggregator, value);
        };

        Runnable daoOperator = () -> {
            TimeStampedValue value = aggregator.updateValueIfNeeded(dao.get(key));
            responseToGetIfNeeded(session, aggregator, value);
        };

        handle(session, request, key, responsibleNodes, aggregator, consumer, daoOperator);
    }

    private void handle(
            HttpSession session, Request request,
            String key,
            Iterable<RandevouzHashingRouter.Node> responsibleNodes,
            ReplicatingResponseCounter counter,
            Consumer<HttpResponse<byte[]>> httpResponseConsumer,
            Runnable daoWork
    ) {
        for (RandevouzHashingRouter.Node node : responsibleNodes) {
            if (selfUrl.equals(node.url)) {
                CompletableFuture.runAsync(daoWork).exceptionally(throwable -> {
                    counter.responseFailureIfNeeded(session);
                    handleFailure(throwable, selfUrl, request, key);
                    return null;
                });
                continue;
            }
            node.routedRequestFuture(request, key)
                    .orTimeout(ServiceImpl.ROUTED_REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .whenComplete((httpResponse, throwable) -> {
                        if (throwable == null) {
                            httpResponseConsumer.accept(httpResponse);
                            return;
                        }
                        counter.responseFailureIfNeeded(session);
                        handleNodeFailure(throwable, node, request, key);
                    });
        }
    }

    private static void responseToUpsertIfNeeded(HttpSession session, String okMessage, ReplicatingResponseCounter counter) {
        if (counter.isTimeToResponseGood()) {
            ServiceImpl.sendResponse(session, new Response(okMessage, Response.EMPTY));
        }
    }

    private static void responseToGetIfNeeded(HttpSession session, ReplicatingGetAggregator counter, TimeStampedValue value) {
        if (!counter.isTimeToResponseGood()) {
            return;
        }
        if (value == null || value.value == null) {
            ServiceImpl.sendResponse(session, new Response(Response.NOT_FOUND, Response.EMPTY));
        } else {
            ServiceImpl.sendResponse(session, new Response(Response.OK, value.valueBytes()));
        }
    }

    private static void handleFailure(Throwable t, String selfUrl, Request request, String key) {
        ServiceImpl.LOGGER.error(t + " when " + selfUrl + " was proceeding a request method "
                + request.getMethod() + " for " + key);
    }

    private static void handleNodeFailure(Throwable t, RandevouzHashingRouter.Node node, Request request, String key) {
        node.managePossibleIllness();
        ServiceImpl.LOGGER.error(t + " when requesting a " + node.url + " to respond to an "
                + request.getMethod() + " method for " + key);
    }

    private static TimeStampedValue valueFromResponse(HttpResponse<byte[]> httpResponse) {
        if (httpResponse.statusCode() != 200 && httpResponse.statusCode() != 404) {
            return null;
        }
        if (httpResponse.statusCode() == 404) {
            if (httpResponse.body().length == 0) {
                return null;
            }
            return TimeStampedValue.tombstoneFromTime(httpResponse.body());
        } else {
            return TimeStampedValue.fromBytes(httpResponse.body());
        }
    }

}
