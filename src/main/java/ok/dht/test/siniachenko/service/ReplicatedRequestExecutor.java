package ok.dht.test.siniachenko.service;

import ok.dht.test.siniachenko.TycoonHttpServer;
import ok.dht.test.siniachenko.exception.NotEnoughReplicasException;
import ok.dht.test.siniachenko.exception.ServiceInternalErrorException;
import ok.dht.test.siniachenko.exception.ServiceUnavailableException;
import ok.dht.test.siniachenko.nodemapper.NodeMapper;
import ok.dht.test.siniachenko.nodetaskmanager.NodeTaskManager;
import one.nio.http.Request;
import one.nio.util.Utf8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class ReplicatedRequestExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(ReplicatedRequestExecutor.class);
    private static final Map<String, Set<Integer>> METHOD_NAME_TO_SUCCESS_STATUS_CODES = Map.of(
        "GET", Set.of(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_NOT_FOUND),
        "PUT", Collections.singleton(HttpURLConnection.HTTP_CREATED),
        "DELETE", Collections.singleton(HttpURLConnection.HTTP_ACCEPTED)
    );

    private final Request request;
    private final String id;
    private final Supplier<byte[]> localWork;
    private final int ack;
    private final int from;

    Set<Integer> successStatusCodes;
    byte[][] bodies;
    CountDownLatch countDownLatch;
    AtomicInteger successCount;
    boolean needLocalWork;
    int localIndex;

    public ReplicatedRequestExecutor(Request request, String id, Supplier<byte[]> localWork, int ack, int from) {
        this.request = request;
        this.id = id;
        this.localWork = localWork;
        this.ack = ack;
        this.from = from;
    }

    public byte[][] execute(
        String selfUrl, NodeMapper nodeMapper, NodeTaskManager nodeTaskManager, HttpClient httpClient
    ) throws ServiceUnavailableException, NotEnoughReplicasException, ServiceInternalErrorException {
        successStatusCodes = METHOD_NAME_TO_SUCCESS_STATUS_CODES.get(request.getMethodName());

        bodies = new byte[from][];
        countDownLatch = new CountDownLatch(from);
        successCount = new AtomicInteger();

        needLocalWork = false;
        localIndex = 0;

        NodeMapper.Shard[] shards = nodeMapper.shards;
        int nodeIndex = nodeMapper.getIndexForKey(Utf8.toBytes(id));
        for (int replicaIndex = 0; replicaIndex < from; ++replicaIndex) {
            NodeMapper.Shard shard = shards[(nodeIndex + replicaIndex) % shards.length];
            String nodeUrlByKey = shard.getUrl();
            if (selfUrl.equals(nodeUrlByKey)) {
                needLocalWork = true;
                localIndex = replicaIndex;
            } else {
                proxyAndHandle(nodeTaskManager, httpClient, replicaIndex, nodeUrlByKey);
            }
        }
        if (needLocalWork) {
            try {
                bodies[localIndex] = localWork.get();
                successCount.incrementAndGet();
            } finally {
                countDownLatch.countDown();
            }
        }

        try {
            countDownLatch.await();
            if (successCount.get() < ack) {
                throw new NotEnoughReplicasException();
            }
            return bodies;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceInternalErrorException(e);
        }
    }

    private void proxyAndHandle(
        NodeTaskManager nodeTaskManager, HttpClient httpClient, int replicaIndex, String nodeUrlByKey
    ) throws ServiceUnavailableException {
        boolean taskAdded = nodeTaskManager.tryAddNodeTask(nodeUrlByKey, () -> {
            try {
                HttpResponse<byte[]> response = proxyRequest(
                    request, id, nodeUrlByKey, httpClient
                );
                if (successStatusCodes.contains(response.statusCode())) {
                    bodies[replicaIndex] = response.body();
                    successCount.incrementAndGet();
                } else {
                    LOG.error(
                        "Unexpected status code {} after proxy request to {}",
                        response.statusCode(),
                        nodeUrlByKey
                    );
                }
            } catch (IOException | InterruptedException e) {
                LOG.error("Error after proxy request to {}", nodeUrlByKey, e);
            } finally {
                countDownLatch.countDown();
            }
        });
        if (!taskAdded) {
            throw new ServiceUnavailableException();
        }
    }

    private HttpResponse<byte[]> proxyRequest(
        Request request, String idParameter, String nodeUrl, HttpClient httpClient
    ) throws IOException, InterruptedException {
        return httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create(nodeUrl + TycoonHttpServer.PATH + "?id=" + idParameter))
                .method(
                    request.getMethodName(),
                    request.getBody() == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(request.getBody()))
                .header(TycoonHttpServer.REQUEST_TO_REPLICA_HEADER, "")
                .build(),
            HttpResponse.BodyHandlers.ofByteArray()
        );
    }
}
