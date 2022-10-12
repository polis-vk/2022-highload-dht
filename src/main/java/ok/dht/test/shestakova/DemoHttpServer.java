package ok.dht.test.shestakova;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import ok.dht.ServiceConfig;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;

public class DemoHttpServer extends HttpServer {

    private final HttpClient httpClient;
    private final ServiceConfig serviceConfig;
    private final ExecutorService workersPool;

    public DemoHttpServer(HttpServerConfig config, HttpClient httpClient, ExecutorService workersPool,
                          ServiceConfig serviceConfig, Object... routers) throws IOException {
        super(config, routers);
        this.httpClient = httpClient;
        this.serviceConfig = serviceConfig;
        this.workersPool = workersPool;
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

        String targetCluster = getClusterByRendezvousHashing(key);
        assert targetCluster != null;
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
            } catch (InterruptedException | TimeoutException | IOException e) {
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
            throws TimeoutException, InterruptedException, IOException {
        try {
            HttpResponse<byte[]> response = responseCompletableFuture.get(10, TimeUnit.SECONDS);
            session.sendResponse(new Response(
                    String.valueOf(response.statusCode()),
                    response.body()
            ));
        } catch (ExecutionException e) {
            session.sendResponse(new Response(
                    Response.SERVICE_UNAVAILABLE,
                    Response.EMPTY
            ));
        }
    }

    private String getClusterByRendezvousHashing(String key) {
        int hashVal = Integer.MIN_VALUE;
        String cluster = null;
        final int keyHash = key.hashCode();

        for (String clusterUrl : serviceConfig.clusterUrls()) {
            int tmpHash = getHashCodeForTwoElements(keyHash, clusterUrl);
            if (tmpHash > hashVal) {
                hashVal = tmpHash;
                cluster = clusterUrl;
            }
        }
        return cluster;
    }

    private int getHashCodeForTwoElements(int hash, String s) {
        int h = hash;
        for (int i = 0; i < s.length(); i++) {
            h = 31 * h + s.charAt(i);
        }
        return h;
    }
}
