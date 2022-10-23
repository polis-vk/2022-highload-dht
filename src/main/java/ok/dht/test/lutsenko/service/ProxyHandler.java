package ok.dht.test.lutsenko.service;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ProxyHandler implements Closeable {

    private static final int RESPONSE_TIMEOUT_SECONDS = 5;
    private static final int MAX_NODE_FAILS_NUMBER = 5;
    private static final int NODE_AS_UNAVAILABLE_DURATION_MILLIS = 180_000;
    private static final int UNAVAILABLE_NODES_CLEAN_INTERVAL_MILLIS = NODE_AS_UNAVAILABLE_DURATION_MILLIS / 2;
    private static final Duration CLIENT_TIMEOUT = Duration.of(5, ChronoUnit.SECONDS);
    private static final Logger LOG = LoggerFactory.getLogger(ProxyHandler.class);

    private final Map<String, Long> unavailableNodes = new ConcurrentSkipListMap<>();
    private final Map<String, AtomicInteger> nodesFailsNumberMap = new ConcurrentSkipListMap<>();
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
        LOG.info("Unavailable nodes cleaner: " + !unusedScheduledFuture.isCancelled());
    }

    public CompletableFuture<Response> proceed(Request request, String externalUrl, long requestTime) {
        return CompletableFuture.supplyAsync(() -> {
            if (unavailableNodes.containsKey(externalUrl)) {
                return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
            }
            try {
                HttpResponse<byte[]> httpResponse = proxyRequestAsync(request, externalUrl, requestTime)
                        .get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                return ServiceUtils.toResponse(httpResponse);
            } catch (Exception e) {
                LOG.error("Failed while getting response from external node", e);
                nodesFailsNumberMap.putIfAbsent(externalUrl, new AtomicInteger(0));
                if (nodesFailsNumberMap.get(externalUrl).incrementAndGet() >= MAX_NODE_FAILS_NUMBER) {
                    unavailableNodes.put(externalUrl, System.currentTimeMillis() + NODE_AS_UNAVAILABLE_DURATION_MILLIS);
                    nodesFailsNumberMap.remove(externalUrl);
                }
                return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
            }
        });

    }
    private CompletableFuture<HttpResponse<byte[]>> proxyRequestAsync(Request request, String externalUrl, long requestTime) {
        return httpClient.sendAsync(
                HttpRequest.newBuilder(URI.create(externalUrl + request.getURI()))
                        .method(
                                request.getMethodName(),
                                request.getBody() == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofByteArray(request.getBody())
                        )
                        .setHeader(ServiceUtils.trim(CustomHeaders.PROXY_REQUEST_TIME), String.valueOf(requestTime))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
    }

    @Override
    public void close() throws IOException {
        unavailableNodes.clear();
        nodesFailsNumberMap.clear();
        unavailableNodesCleaner.shutdownNow();
    }
}
