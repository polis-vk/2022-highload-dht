package ok.dht.test.ilin.replica;

import ok.dht.test.ilin.domain.Headers;
import ok.dht.test.ilin.domain.ReplicasInfo;
import ok.dht.test.ilin.hashing.impl.ConsistentHashing;
import ok.dht.test.ilin.service.EntityService;
import ok.dht.test.ilin.sharding.ShardHandler;
import ok.dht.test.ilin.utils.TimestampUtils;
import one.nio.http.Request;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class ReplicasHandler {
    private final Logger logger = LoggerFactory.getLogger(ReplicasHandler.class);
    private final String selfAddress;
    private final EntityService entityService;
    private final ConsistentHashing consistentHashing;
    private final ShardHandler shardHandler;
    private final ExecutorService executorService;

    public ReplicasHandler(
        String selfAddress,
        EntityService entityService,
        ConsistentHashing consistentHashing,
        ShardHandler shardHandler
    ) {
        this.selfAddress = selfAddress;
        this.entityService = entityService;
        this.consistentHashing = consistentHashing;
        this.shardHandler = shardHandler;
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(1024);
        this.executorService = new ThreadPoolExecutor(
            4,
            4,
            0L,
            TimeUnit.MILLISECONDS,
            queue
        );
    }

    public CompletableFuture<Response> execute(String key, ReplicasInfo replicasInfo, Request request) {
        Set<String> addresses = consistentHashing.getServerAddressesForKey(key, replicasInfo.from());

        List<CompletableFuture<Response>> futures = new ArrayList<>();
        for (String address : addresses) {
            if (selfAddress.equals(address)) {
                futures.add(selfExecute(key, request));
            } else {
                futures.add(shardHandler.executeOnAddress(address, key, request));
            }
        }

        CompletableFuture<Void> result = new CompletableFuture<>();
        AtomicReferenceArray<Response> responses = new AtomicReferenceArray<>(futures.size());
        AtomicInteger acks = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger(replicasInfo.from() - replicasInfo.ack());

        for (int i = 0; i < futures.size(); i++) {
            final int current = i;
            futures.get(i).handleAsync((response, throwable) -> {
                if (throwable != null) {
                    if (throwable instanceof CompletionException) {
                        throwable = throwable.getCause();
                    }
                    if (throwable instanceof IOException) {
                        logger.warn("Node is not available: {}", throwable.getMessage());
                        if (failures.getAndDecrement() == 0) {
                            result.complete(null);
                        }
                        return null;
                    }
                    logger.error("Error in handle request: {}", throwable.getMessage());
                    result.completeExceptionally(throwable);
                    return null;
                }
                int status = response.getStatus();
                if (status != 201 && status != 200 && status != 202 && status != 404 && status != 405) {
                    logger.debug("result is not expected from node, status: {}", status);
                    if (failures.getAndDecrement() == 0) {
                        result.complete(null);
                    }
                    return null;
                }
                responses.set(current, response);
                if (acks.incrementAndGet() == replicasInfo.ack()) {
                    result.complete(null);
                }
                return null;
            }, executorService).exceptionally(throwable -> {
                result.completeExceptionally(throwable);
                return null;
            });
        }
        return result.thenApplyAsync(ignored -> {
            if (failures.get() < 0) {
                return new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY);
            }
            List<Response> resultList = new ArrayList<>();
            for (int i = 0; i < responses.length(); i++) {
                Response data = responses.get(i);
                if (data == null) {
                    continue;
                }

                resultList.add(data);
                if (resultList.size() == replicasInfo.ack()) {
                    break;
                }
            }
            return switch (request.getMethod()) {
                case Request.METHOD_GET -> mergeResponsesWithTimestamp(resultList, replicasInfo);
                case Request.METHOD_PUT -> mergeResponsesWithCode(resultList, replicasInfo, 201, Response.CREATED);
                case Request.METHOD_DELETE -> mergeResponsesWithCode(resultList, replicasInfo, 202, Response.ACCEPTED);
                default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            };
        });
    }

    public CompletableFuture<Response> selfExecute(String key, Request request) {
        return CompletableFuture.supplyAsync(() -> switch (request.getMethod()) {
            case Request.METHOD_GET -> entityService.getEntity(key);
            case Request.METHOD_PUT -> entityService.upsertEntity(key, request);
            case Request.METHOD_DELETE -> entityService.deleteEntity(key, request);
            default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
        }, executorService);
    }

    private Response mergeResponsesWithTimestamp(List<Response> responses, ReplicasInfo replicasInfo) {
        if (responses.size() < replicasInfo.ack()) {
            return new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY);
        }
        int ack = replicasInfo.ack();
        Response result = null;
        boolean isResultTombstone = false;
        long bestTimestamp = Long.MIN_VALUE;
        for (Response response : responses) {
            if (response.getStatus() == 200 || response.getStatus() == 404) {
                ack--;
                long timestamp = TimestampUtils.extractTimestamp(response);
                if (timestamp > bestTimestamp || result == null) {
                    bestTimestamp = timestamp;
                    result = response;
                    continue;
                }
                if (timestamp == bestTimestamp) {
                    if (response.getHeader(Headers.TOMBSTONE_HEADER) != null) {
                        result = response;
                        isResultTombstone = true;
                    } else if (!isResultTombstone) {
                        if (Arrays.compare(result.getBody(), response.getBody()) < 0) {
                            result = response;
                        }
                    }
                }
            }
        }
        if (ack > 0 || result == null) {
            return new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY);
        }
        return result;
    }

    private Response mergeResponsesWithCode(
        List<Response> responses,
        ReplicasInfo replicasInfo,
        int expectedCode,
        String answer
    ) {
        int ack = replicasInfo.ack();
        for (Response response : responses) {
            if (response.getStatus() != expectedCode) {
                continue;
            }
            ack--;
            if (ack == 0) {
                return new Response(answer, Response.EMPTY);
            }
        }
        return new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY);
    }
}
