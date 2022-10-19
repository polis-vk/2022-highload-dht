package ok.dht.test.kuleshov;

import one.nio.http.HttpClient;
import one.nio.http.HttpException;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.ConnectionString;
import one.nio.pool.PoolException;
import one.nio.util.Hash;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CoolAsyncHttpServer extends CoolHttpServer {
    private static final int CORE_POOL_SIZE = 8;
    private static final int MAXIMUM_POOL_SIZE = 8;

    private final String selfUrl;
    private ExecutorService executorService;
    private final TreeSet<Integer> treeSet = new TreeSet<>();
    private final Map<Integer, String> hashToHost = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, HttpClient> hashToClient = new ConcurrentHashMap<>();

    public CoolAsyncHttpServer(HttpServerConfig config, Service service, Object... routers) throws IOException {
        super(config, service, routers);

        selfUrl = service.getConfig().selfUrl();
        List<String> clusters = service.getConfig().clusterUrls();

        int n = clusters.size();

        int startRangeSize = Integer.MAX_VALUE / n;
        int cur = startRangeSize;

        for (String cluster : clusters) {
            treeSet.add(cur);
            hashToClient.put(cur, new HttpClient(new ConnectionString(cluster)));
            hashToHost.put(cur, cluster);
            cur += startRangeSize;
        }
    }

    private Response sendGet(int node, String id) throws HttpException, IOException, PoolException {
        try {
            return hashToClient.get(node).get("/v1/entity?id=" + id);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }

        return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
    }

    private Response sendDelete(int node, String id) throws HttpException, IOException, PoolException {
        try {
            return hashToClient.get(node).delete("/v1/entity?id=" + id);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }

        return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
    }

    private Response sendPut(int node, String id, byte[] body, String headers) throws HttpException, IOException, PoolException {
        try {
            return hashToClient.get(node).put("/v1/entity?id=" + id, body, headers);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }

        return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
    }

    private Integer getVirtualNodeNumber(String id) {
        int hash = Hash.murmur3(id);

        Integer next = treeSet.ceiling(hash);

        if (next != null) {
            return next;
        } else {
            return treeSet.ceiling(Integer.MIN_VALUE);
        }
    }

    @Override
    public synchronized void start() {
        super.start();
        executorService = new ThreadPoolExecutor(CORE_POOL_SIZE,
                MAXIMUM_POOL_SIZE,
                100,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(128)
        );
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        executorService.execute(() -> {
            try {
                int method = request.getMethod();
                if (!SUPPORTED_METHODS.contains(method)) {
                    session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));

                    return;
                }

                String path = request.getPath();
                if (path.equals("v1/entity")) {
                    session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));

                    return;
                }

                String id = request.getParameter("id=");
                if (id == null || id.isBlank()) {
                    session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));

                    return;
                }

                Integer number = getVirtualNodeNumber(id);

                if (number == null) {
                    session.sendResponse(new Response(Response.NOT_FOUND, Response.EMPTY));

                    return;
                }

                if (!hashToHost.get(number).equals(selfUrl)) {
                    switch (method) {
                        case Request.METHOD_GET -> session.sendResponse(sendGet(number, id));
                        case Request.METHOD_PUT -> session.sendResponse(sendPut(number, id, request.getBody(), request.getHeaders()[0]));
                        case Request.METHOD_DELETE -> session.sendResponse(sendDelete(number, id));
                    }
                    return;
                }

                session.sendResponse(service.handle(method, id, request));
            } catch (Exception e) {
                try {
                    session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                } catch (IOException ex) {
                    //ignore
                }
            }
        });
    }

    @Override
    public synchronized void stop() {
        boolean isFinished = false;
        try {
            isFinished = executorService.awaitTermination(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }



        if (isFinished) {
            executorService.shutdown();
        }

        super.stop();
    }
}
