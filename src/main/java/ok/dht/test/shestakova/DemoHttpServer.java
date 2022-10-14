package ok.dht.test.shestakova;

import ok.dht.ServiceConfig;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;
import one.nio.util.Hash;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

public class DemoHttpServer extends HttpServer {

    private final HttpClient httpClient;
    private final ServiceConfig serviceConfig;
    private final ExecutorService workersPool;
    private final AtomicLong fallenRequestCount = new AtomicLong();
    private final Timer timer = new Timer();
    private final Map<String, Boolean> nodesIllness;
    private int illPeriodsCounter;

    private boolean isIll;

    public DemoHttpServer(HttpServerConfig config, HttpClient httpClient, ExecutorService workersPool,
                          ServiceConfig serviceConfig, Object... routers) throws IOException {
        super(config, routers);
        this.httpClient = httpClient;
        this.serviceConfig = serviceConfig;
        this.workersPool = workersPool;
        this.timer.schedule(
                new BreakerTimerTask(),
                0L,
                5000L
        );
        this.nodesIllness = new HashMap<>();
        for (String clusterUrl : this.serviceConfig.clusterUrls()) {
            nodesIllness.put(clusterUrl, false);
        }
    }

    @Override
    public void handleRequest(Request request, HttpSession session) {
        String key = request.getParameter("id=");
        if (key == null || key.isEmpty()) {
            try {
                handleDefault(request, session);
                return;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        String targetCluster = serviceConfig.clusterUrls().size() > 1 ? getClusterByRendezvousHashing(key)
                : serviceConfig.selfUrl();
        if (targetCluster == null) {
            throw new RuntimeException("There are no available nodes in the cluster!");
        }
        if (!targetCluster.equals(serviceConfig.selfUrl())) {
            try {
                HttpRequest httpRequest = buildHttpRequest(key, targetCluster, request);
                if (httpRequest == null) {
                    handleDefault(request, session);
                    return;
                }
                CompletableFuture<HttpResponse<byte[]>> responseCompletableFuture = httpClient
                        .sendAsync(
                                httpRequest,
                                HttpResponse.BodyHandlers.ofByteArray()
                        );
                getResponse(responseCompletableFuture, session);
                return;
            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        workersPool.execute(() -> {
            try {
                super.handleRequest(request, session);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response;
        int requestMethod = request.getMethod();
        if (requestMethod == Request.METHOD_GET
                || requestMethod == Request.METHOD_PUT
                || requestMethod == Request.METHOD_DELETE) {
            response = new Response(
                    Response.BAD_REQUEST,
                    Response.EMPTY
            );
        } else {
            response = new Response(
                    Response.METHOD_NOT_ALLOWED,
                    Response.EMPTY
            );
        }
        session.sendResponse(response);
    }

    @Override
    public synchronized void stop() {
        for (SelectorThread selectorThread : selectors) {
            if (!selectorThread.isAlive()) {
                continue;
            }
            for (Session session : selectorThread.selector) {
                session.close();
            }
        }
        super.stop();
    }

    public boolean isIll() {
        return isIll;
    }

    public void putNodesIllnessInfo(String node, boolean isIll) {
        nodesIllness.put(node, isIll);
    }

    private HttpRequest.Builder request(String path, String clusterUrl) {
        return HttpRequest.newBuilder(URI.create(clusterUrl + path));
    }

    private HttpRequest.Builder requestForKey(String key, String clusterUrl) {
        return request("/v0/entity?id=" + key, clusterUrl);
    }

    private HttpRequest buildHttpRequest(String key, String targetCluster, Request request) {
        HttpRequest.Builder httpRequest = requestForKey(key, targetCluster);
        int requestMethod = request.getMethod();
        if (requestMethod == Request.METHOD_GET) {
            httpRequest.GET();
        } else if (requestMethod == Request.METHOD_PUT) {
            httpRequest.PUT(HttpRequest.BodyPublishers.ofByteArray(request.getBody()));
        } else if (requestMethod == Request.METHOD_DELETE) {
            httpRequest.DELETE();
        } else {
            return null;
        }
        return httpRequest.build();
    }

    private void getResponse(CompletableFuture<HttpResponse<byte[]>> responseCompletableFuture, HttpSession session)
            throws InterruptedException, IOException {
        try {
            HttpResponse<byte[]> response = responseCompletableFuture.get(1, TimeUnit.SECONDS);
            session.sendResponse(new Response(
                    String.valueOf(response.statusCode()),
                    response.body()
            ));
        } catch (ExecutionException | TimeoutException e) {
            fallenRequestCount.incrementAndGet();
            session.sendResponse(new Response(
                    Response.SERVICE_UNAVAILABLE,
                    Response.EMPTY
            ));
        }
    }

    private String getClusterByRendezvousHashing(String key) {
        int hashVal = Integer.MIN_VALUE;
        String cluster = null;

        for (String clusterUrl : serviceConfig.clusterUrls()) {
            if (nodesIllness.get(clusterUrl)) {
                continue;
            }
            int tmpHash = Hash.murmur3(clusterUrl + key);
            if (tmpHash > hashVal) {
                hashVal = tmpHash;
                cluster = clusterUrl;
            }
        }
        return cluster;
    }

    // Пока что проверка здоровья ноды не придумана, и мы просто даём ноде 10 периодов по 5 секунд на восстановление
    // и снова начинаем с ней работать (если она все еще больна, мы это поймём через 1 период - 5 секунд)
    private boolean isStillIll() {
        return illPeriodsCounter > 10;
    }

    private class BreakerTimerTask extends TimerTask {
        @Override
        public void run() {
            if (fallenRequestCount.get() > 1000) {
                isIll = true;
                nodesIllness.put(serviceConfig.selfUrl(), true);
                for (String clusterUrl : serviceConfig.clusterUrls()) {
                    try {
                        httpClient.send(
                                request("/service/message", clusterUrl)
                                        .PUT(HttpRequest.BodyPublishers.ofString(clusterUrl))
                                        .build(),
                                HttpResponse.BodyHandlers.ofByteArray()
                        );
                    } catch (IOException | InterruptedException e) {
                       throw new RuntimeException(e);
                    }
                }
            }
            fallenRequestCount.getAndSet(0);
            illPeriodsCounter++;
            if (isIll && !isStillIll()) {
                isIll = false;
                nodesIllness.put(serviceConfig.selfUrl(), false);
                illPeriodsCounter = 0;
            }
        }
    }
}
