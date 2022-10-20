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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static ok.dht.test.kuleshov.utils.ResponseUtils.emptyResponse;

public class CoolAsyncHttpServer extends CoolHttpServer {
    private static final int CORE_POOL_SIZE = 4;
    private static final int MAXIMUM_POOL_SIZE = 4;

    private final String selfUrl;
    private ExecutorService workerExecutorService;
    private ExecutorService senderExecutorService;
    private final TreeSet<Integer> treeSet = new TreeSet<>();
    private final Map<Integer, String> hashToHost = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, HttpClient> hashToClient = new ConcurrentHashMap<>();

    public CoolAsyncHttpServer(HttpServerConfig config, Service service, Object... routers) throws IOException {
        super(config, service, routers);

        selfUrl = service.getConfig().selfUrl();
        List<String> clusters = service.getConfig().clusterUrls();

        int n = clusters.size();
        clusters.sort(Comparator.naturalOrder());

        int startRangeSize = Integer.MAX_VALUE / n;
        int cur = startRangeSize;

        for (String cluster : clusters) {
            treeSet.add(cur);
            hashToClient.put(cur, new HttpClient(new ConnectionString(cluster)));
            hashToHost.put(cur, cluster);
            cur += startRangeSize;
        }
    }

    @Override
    public synchronized void start() {
        super.start();
        workerExecutorService = new ThreadPoolExecutor(CORE_POOL_SIZE,
                MAXIMUM_POOL_SIZE,
                100,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(128)
        );

        senderExecutorService = new ThreadPoolExecutor(CORE_POOL_SIZE,
                MAXIMUM_POOL_SIZE,
                100,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(128)
        );
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
                if (path.equals("v1/entity")) {
                    session.sendResponse(emptyResponse(Response.BAD_REQUEST));

                    return;
                }

                String id = request.getParameter("id=");
                if (id == null || id.isBlank()) {
                    session.sendResponse(emptyResponse(Response.BAD_REQUEST));

                    return;
                }

                Integer number = getVirtualNodeNumber(id);

                if (number == null) {
                    session.sendResponse(emptyResponse(Response.NOT_FOUND));

                    return;
                }

                if (!hashToHost.get(number).equals(selfUrl)) {
                    senderExecutorService.execute(() -> {
                        try {
                            session.sendResponse(sendRequest(number, id, request));
                        } catch (IOException | HttpException | PoolException e) {
                            e.printStackTrace();
                        }
                    });
                    return;
                }

                session.sendResponse(service.handle(method, id, request));
            } catch (Exception e) {
                try {
                    session.sendResponse(emptyResponse(Response.BAD_REQUEST));
                } catch (IOException ex) {
                    //ignore
                }
            }
        });
    }

    @Override
    public synchronized void stop() {
        terminateExecutor(workerExecutorService);
        terminateExecutor(senderExecutorService);

        super.stop();
    }

    private Response sendRequest(
            int node,
            String id,
            Request request
    ) throws HttpException, IOException, PoolException {
        switch (request.getMethod()) {
            case Request.METHOD_GET -> {
                return sendGet(node, id);
            }
            case Request.METHOD_PUT -> {
                return sendPut(node, id, request.getBody());
            }
            case Request.METHOD_DELETE -> {
                return sendDelete(node, id);
            }
            default -> {
                return emptyResponse(Response.METHOD_NOT_ALLOWED);
            }
        }
    }

    private Response sendGet(int node, String id) throws HttpException, IOException, PoolException {
        try {
            return hashToClient.get(node).get("/v1/entity?id=" + id);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }

        return emptyResponse(Response.INTERNAL_ERROR);
    }

    private Response sendDelete(int node, String id) throws HttpException, IOException, PoolException {
        try {
            return hashToClient.get(node).delete("/v1/entity?id=" + id);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }

        return emptyResponse(Response.INTERNAL_ERROR);
    }

    private Response sendPut(int node, String id, byte[] body) throws HttpException, IOException, PoolException {
        try {
            return hashToClient.get(node).put("/v1/entity?id=" + id, body);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }

        return emptyResponse(Response.INTERNAL_ERROR);
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
