package ok.dht.test.ilin.replica;

import ok.dht.test.ilin.domain.HeadersUtils;
import ok.dht.test.ilin.domain.ReplicasInfo;
import ok.dht.test.ilin.hashing.impl.ConsistentHashing;
import ok.dht.test.ilin.service.EntityService;
import ok.dht.test.ilin.sharding.ShardHandler;
import ok.dht.test.ilin.utils.TimestampUtils;
import one.nio.http.Request;
import one.nio.http.Response;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ReplicasHandler {
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

    public Response execute(String key, ReplicasInfo replicasInfo, Request request) {
        Set<String> addresses = consistentHashing.getServerAddressesForKey(key, replicasInfo.from());

        List<Future<Response>> futures = new ArrayList<>();
        for (String address : addresses) {
            var future = executorService.submit(() -> {
                if (selfAddress.equals(address)) {
                    return selfExecute(key, request);
                }
                return shardHandler.executeOnAddress(address, key, request);
            });
            futures.add(future);
        }

        List<Response> responses = new ArrayList<>();
        for (Future<Response> future : futures) {
            try {
                Response response = future.get();
                int status = response.getStatus();
                if (status == 201 || status == 200 || status == 202 || status == 404 || status == 405) {
                    responses.add(response);
                    if (responses.size() == replicasInfo.ack()) {
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (ExecutionException e) {
                throw new IllegalStateException(e);
            }
        }

        if (responses.size() < replicasInfo.ack()) {
            return new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY);
        }

        return switch (request.getMethod()) {
            case Request.METHOD_GET -> mergeResponsesWithTimestamp(responses, replicasInfo);
            case Request.METHOD_PUT -> mergeResponsesWithCode(responses, replicasInfo, 201, Response.CREATED);
            case Request.METHOD_DELETE -> mergeResponsesWithCode(responses, replicasInfo, 202, Response.ACCEPTED);
            default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
        };
    }

    public Response selfExecute(String key, Request request) {
        return switch (request.getMethod()) {
            case Request.METHOD_GET -> entityService.getEntity(key);
            case Request.METHOD_PUT -> entityService.upsertEntity(key, request);
            case Request.METHOD_DELETE -> entityService.deleteEntity(key, request);
            default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
        };
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
                    if (response.getHeader(HeadersUtils.TOMBSTONE_HEADER) != null) {
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
