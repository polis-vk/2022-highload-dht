package ok.dht.test.lutsenko.service;

import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class ProxyHandler implements Closeable {

    private static final int EXECUTOR_THREADS = 10;
    private static final int RESPONSE_TIMEOUT_SECONDS = 5;
    private static final int MAX_NODE_TIMEOUTS_NUMBER = 5;
    private static final int NODE_AS_UNAVAILABLE_DURATION_MILLIS = 180_000;
    private static final int UNAVAILABLE_NODES_CLEAN_INTERVAL_MILLIS = NODE_AS_UNAVAILABLE_DURATION_MILLIS / 2;
    private static final Duration CLIENT_TIMEOUT = Duration.of(5, ChronoUnit.SECONDS);
    private static final Logger LOG = LoggerFactory.getLogger(ProxyHandler.class);

    private final Map<String, Long> unavailableNodes = new ConcurrentSkipListMap<>();
    private final Map<String, AtomicInteger> nodesTimeoutsNumberMap = new ConcurrentSkipListMap<>();
    private final ExecutorService proxyRequestExecutor = Executors.newFixedThreadPool(EXECUTOR_THREADS);
    private final ScheduledExecutorService unavailableNodesCleaner = Executors.newSingleThreadScheduledExecutor();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CLIENT_TIMEOUT)
            .executor(Executors.newFixedThreadPool(2))
            .build();

    public ProxyHandler() {
        // unused variable due to warnings
        ScheduledFuture<?> unusedScheduledFuture = unavailableNodesCleaner.scheduleWithFixedDelay(
                () -> unavailableNodes.entrySet().removeIf(entry -> System.currentTimeMillis() > entry.getValue()),
                0,
                UNAVAILABLE_NODES_CLEAN_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS
        );
        LOG.info(unusedScheduledFuture.toString());
    }

    public void handle(Request request, HttpSession session, String externalUrl) {
        if (unavailableNodes.containsKey(externalUrl)) {
            ServiceUtils.sendResponse(session, Response.SERVICE_UNAVAILABLE);
            return;
        }
        proxyRequestExecutor.execute(new SessionRunnable(session, () -> {
            try {
                HttpResponse<byte[]> httpResponse = proxyRequestAsync(request, externalUrl)
                        .get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                ServiceUtils.sendResponse(session, httpResponse);
            } catch (TimeoutException te) {
                LOG.error("Timeout while getting response from external node", te);
                nodesTimeoutsNumberMap.putIfAbsent(externalUrl, new AtomicInteger(0));
                if (nodesTimeoutsNumberMap.get(externalUrl).incrementAndGet() >= MAX_NODE_TIMEOUTS_NUMBER) {
                    unavailableNodes.put(externalUrl, System.currentTimeMillis() + NODE_AS_UNAVAILABLE_DURATION_MILLIS);
                    nodesTimeoutsNumberMap.remove(externalUrl);
                }
                ServiceUtils.sendResponse(session, Response.GATEWAY_TIMEOUT);
            } catch (Exception e) {
                LOG.error("Failed to get response from external url", e);
                unavailableNodes.put(externalUrl, System.currentTimeMillis() + NODE_AS_UNAVAILABLE_DURATION_MILLIS);
                ServiceUtils.sendResponse(session, Response.SERVICE_UNAVAILABLE);
            }
        }));
    }

    private CompletableFuture<HttpResponse<byte[]>> proxyRequestAsync(Request request, String externalUrl) {
        return httpClient.sendAsync(
                HttpRequest.newBuilder(URI.create(externalUrl + request.getURI()))
                        .method(
                                request.getMethodName(),
                                request.getBody() == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofByteArray(request.getBody())
                        )
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
    }

    @Override
    public void close() throws IOException {
        try {
            unavailableNodesCleaner.shutdownNow();
            RequestExecutorService.shutdownAndAwaitTermination(proxyRequestExecutor);
        } catch (TimeoutException e) {
            LOG.warn("Proxy request executor executor await termination too long");
        }
    }
}
