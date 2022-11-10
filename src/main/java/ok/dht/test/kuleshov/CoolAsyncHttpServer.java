package ok.dht.test.kuleshov;

import ok.dht.test.kuleshov.utils.RequestsUtils;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.util.Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static ok.dht.test.kuleshov.Validator.isCorrectAckFrom;
import static ok.dht.test.kuleshov.utils.RequestsUtils.getTimestampHeader;
import static ok.dht.test.kuleshov.utils.ResponseUtils.emptyResponse;

public class CoolAsyncHttpServer extends CoolHttpServer {
    private static final String TIMESTAMP_HEADER = "timestamp";

    private static final int WORKER_CORE_POOL_SIZE = 4;
    private static final int WORKER_MAXIMUM_POOL_SIZE = 4;
    private static final int SENDER_CORE_POOL_SIZE = 4;
    private static final int SENDER_MAXIMUM_POOL_SIZE = 4;

    private final int defaultFrom;
    private final int defaultAck;
    private final String selfUrl;
    private ExecutorService workerExecutorService;
    private final NavigableSet<Integer> treeSet = new TreeSet<>();
    private final List<String> clusters;
    private final Map<Integer, Integer> hashToClusterIndex = new ConcurrentHashMap<>();
    private HttpClient httpClient;
    private final Logger log = LoggerFactory.getLogger(CoolAsyncHttpServer.class);

    public CoolAsyncHttpServer(HttpServerConfig config, Service service, Object... routers) throws IOException {
        super(config, service, routers);

        selfUrl = service.getConfig().selfUrl();
        clusters = service.getConfig().clusterUrls();
        clusters.sort(Comparator.naturalOrder());

        int startRangeSize = Integer.MAX_VALUE / clusters.size();
        int cur = startRangeSize;
        defaultFrom = clusters.size();
        defaultAck = defaultFrom / 2 + 1;

        for (int i = 0; i < clusters.size(); i++) {
            treeSet.add(cur);
            hashToClusterIndex.put(cur, i);
            cur += startRangeSize;
        }
    }

    @Override
    public synchronized void start() {
        workerExecutorService = new ThreadPoolExecutor(WORKER_CORE_POOL_SIZE,
                WORKER_MAXIMUM_POOL_SIZE,
                100,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(128)
        );

        ExecutorService senderExecutorService = new ThreadPoolExecutor(SENDER_CORE_POOL_SIZE,
                SENDER_MAXIMUM_POOL_SIZE,
                100,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(128)
        );

        httpClient = HttpClient
                .newBuilder()
                .executor(senderExecutorService)
                .build();

        super.start();
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        workerExecutorService.execute(() -> {
            try {
                int method = request.getMethod();
                if (!SUPPORTED_METHODS.contains(method)) {
                    session.sendResponse(emptyResponse(Response.METHOD_NOT_ALLOWED));

                    return;
                }

                String path = request.getPath();

                if (!"/v0/entity".equals(path) && !"/master/v0/entity".equals(path)) {
                    session.sendResponse(emptyResponse(Response.BAD_REQUEST));

                    return;
                }

                String id = request.getParameter("id=");
                if (id == null || id.isBlank()) {
                    session.sendResponse(emptyResponse(Response.BAD_REQUEST));

                    return;
                }

                if (path.startsWith("/master")) {
                    Response resp = service.handle(method, id, request, getTimestampHeader(request));
                    session.sendResponse(resp);

                    return;
                }

                handleRequest(id, request, session);
            } catch (Exception e) {
                try {
                    session.sendResponse(emptyResponse(Response.BAD_REQUEST));
                } catch (IOException exception) {
                    log.error(exception.getMessage());
                    session.close();
                }
            }
        });
    }

    @Override
    public synchronized void stop() {
        terminateExecutor(workerExecutorService);

        super.stop();
    }

    private void handleRequest(String id, Request request, HttpSession session) {
        Integer parseFrom = RequestsUtils.parseInt(request.getParameter("from="));
        Integer parseAck = RequestsUtils.parseInt(request.getParameter("ack="));

        int from = parseFrom == null ? defaultFrom : parseFrom;
        int ack = parseAck == null ? defaultAck : parseAck;

        if (!isCorrectAckFrom(ack, from, clusters.size())) {
            try {
                session.sendResponse(emptyResponse(Response.BAD_REQUEST));
            } catch (IOException exception) {
                log.error(exception.getMessage());
                session.close();
            }
            return;
        }

        SlaveResponseHandler slaveResponseHandler = new SlaveResponseHandler(ack, from, session);
        long timestamp = System.currentTimeMillis();

        boolean isSelf = false;

        final int startIndex = hashToClusterIndex.get(getVirtualNodeHash(id));

        for (int reqIndex = 0; reqIndex < from; reqIndex++) {
            String slaveUrl = clusters.get((startIndex + reqIndex) % clusters.size());

            if (slaveUrl.equals(selfUrl)) {
                isSelf = true;
                continue;
            }

            HttpRequest requestToSlave = createRequestToSlave(request, slaveUrl, timestamp);
            httpClient.sendAsync(requestToSlave,
                    HttpResponse.BodyHandlers.ofByteArray()
            ).whenComplete((response, exception) -> {
                if (exception == null) {
                    slaveResponseHandler.handleResponse(request.getMethod(), HandleResponse.fromHttpResponse(response));
                } else {
                    slaveResponseHandler.handleFrom();
                }
            });
        }

        if (isSelf) {
            Response selfResponse = service.handle(request.getMethod(), id, request, timestamp);
            slaveResponseHandler.handleResponse(request.getMethod(), HandleResponse.fromOneResponse(selfResponse));
        }
    }

    private HttpRequest createRequestToSlave(Request request, String url, long timestamp) {
        return HttpRequest.newBuilder()
                .method(request.getMethodName(), HttpRequest.BodyPublishers.ofByteArray(request.getBody()))
                .timeout(Duration.of(2, ChronoUnit.SECONDS))
                .header(TIMESTAMP_HEADER, String.valueOf(timestamp))
                .uri(URI.create(url + "/master" + request.getURI()))
                .build();
    }

    private Integer getVirtualNodeHash(String id) {
        int hash = Hash.murmur3(id);

        Integer next = treeSet.ceiling(hash);

        if (next != null) {
            return next;
        }

        return treeSet.ceiling(Integer.MIN_VALUE);
    }

    private static void terminateExecutor(ExecutorService executorService) {
        boolean isFinished = false;
        try {
            isFinished = executorService.awaitTermination(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (isFinished) {
            executorService.shutdown();
        }

    }
}
