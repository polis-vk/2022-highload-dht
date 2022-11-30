package ok.dht.test.yasevich;

import ok.dht.test.yasevich.TimeStampingDao.TimeStampedValue;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

import java.net.http.HttpResponse;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ReplicasManager {
    private static final String NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";

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

    public void handleReplicatingRequest(
            HttpSession session, Request request,
            String key, long time,
            int ack, int from
    ) {
        Queue<RandevouzHashingRouter.Node> responsibleNodes = shardingRouter.responsibleNodes(key, from);
        if (responsibleNodes.isEmpty()) {
            ServiceImpl.LOGGER.error("There is no nodes for handling request");
            ServiceImpl.sendResponse(session, new Response(Response.INTERNAL_ERROR, Response.EMPTY));
            return;
        }
        switch (request.getMethod()) {
            case Request.METHOD_PUT ->
                    handleUpsert(session, request, key, time, responsibleNodes, ack, from, Response.CREATED, 201);
            case Request.METHOD_DELETE ->
                    handleUpsert(session, request, key, time, responsibleNodes, ack, from, Response.ACCEPTED, 202);
            case Request.METHOD_GET -> handleGet(session, request, key, time, responsibleNodes, ack, from);
            default -> ServiceImpl.sendResponse(session, new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
        }
    }

    private void handleUpsert(
            HttpSession session, Request request,
            String key, long time,
            Iterable<RandevouzHashingRouter.Node> responsibleNodes,
            int ack, int from,
            String okMessage, int okStatusCode
    ) {
        ReplicatingResponseCounter counter = new ReplicatingResponseCounter(ack, from);

        Consumer<HttpResponse<byte[]>> consumer = (httpResponse) -> {
            if (httpResponse.statusCode() == okStatusCode) {
                responseToUpsertIfNeeded(session, okMessage, counter);
                return;
            }
            if (counter.isTimeToRespondBad()) {
                responseFailure(session);
            }
        };

        Runnable daoWork = () -> {
            doUpsert(request, key, time);
            responseToUpsertIfNeeded(session, okMessage, counter);
        };

        handle(session, request, key, time, responsibleNodes, counter, consumer, daoWork);
    }

    private void doUpsert(Request request, String key, long time) {
        switch (request.getMethod()) {
            case Request.METHOD_PUT -> dao.upsert(key, request.getBody(), time);
            case Request.METHOD_DELETE -> dao.upsert(key, null, time);
            default -> throw new IllegalArgumentException();
        }
    }

    private void handleGet(
            HttpSession session, Request request,
            String key, long time,
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

        handle(session, request, key, time, responsibleNodes, aggregator, consumer, daoOperator);
    }

    private void handle(
            HttpSession session, Request request,
            String key, long time,
            Iterable<RandevouzHashingRouter.Node> responsibleNodes,
            ReplicatingResponseCounter counter,
            Consumer<HttpResponse<byte[]>> httpResponseConsumer,
            Runnable daoWork
    ) {
        for (RandevouzHashingRouter.Node node : responsibleNodes) {
            if (selfUrl.equals(node.url)) {
                CompletableFuture.runAsync(daoWork).exceptionally(throwable -> {
                    if (counter.isTimeToRespondBad()) {
                        responseFailure(session);
                    }
                    handleFailure(throwable, selfUrl, request, key);
                    return null;
                });
                continue;
            }
            Future<?> future = node.routedRequestFuture(request, key, time)
                    .orTimeout(ServiceImpl.ROUTED_REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .whenComplete((httpResponse, throwable) -> {
                        if (throwable == null) {
                            httpResponseConsumer.accept(httpResponse);
                            return;
                        }
                        if (counter.isTimeToRespondBad()) {
                            responseFailure(session);
                        }
                        handleNodeFailure(throwable, node, request, key);
                    });
            if (future.isDone()) {
                ServiceImpl.LOGGER.debug("Future was done immediately");
            }
        }
    }

    private static void responseToUpsertIfNeeded(
            HttpSession session,
            String okMessage,
            ReplicatingResponseCounter counter
    ) {
        if (counter.isTimeToResponseGood()) {
            ServiceImpl.sendResponse(session, new Response(okMessage, Response.EMPTY));
        }
    }

    private static void responseToGetIfNeeded(
            HttpSession session,
            ReplicatingGetAggregator counter,
            TimeStampedValue value
    ) {
        if (!counter.isTimeToResponseGood()) {
            return;
        }
        if (value == null || value.value == null) {
            ServiceImpl.sendResponse(session, new Response(Response.NOT_FOUND, Response.EMPTY));
        } else {
            ServiceImpl.sendResponse(session, new Response(Response.OK, value.valueBytes()));
        }
    }

    private static void responseFailure(HttpSession session) {
        ServiceImpl.sendResponse(session, new Response(ReplicasManager.NOT_ENOUGH_REPLICAS, Response.EMPTY));
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
