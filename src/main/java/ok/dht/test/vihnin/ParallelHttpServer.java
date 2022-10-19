package ok.dht.test.vihnin;

import ok.dht.ServiceConfig;
import one.nio.http.HttpClient;
import one.nio.http.HttpException;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.ConnectionString;
import one.nio.net.Session;
import one.nio.pool.Pool;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static ok.dht.test.vihnin.ServiceUtils.emptyResponse;

public class ParallelHttpServer extends HttpServer {
    private static final Logger logger = LoggerFactory.getLogger(ParallelHttpServer.class);

    // must be > 2
    private static final int VNODE_NUMBER_PER_SERVER = 5;
    private static final int WORKERS_NUMBER = 8;
    private static final int QUEUE_CAPACITY = 100;

    private static final int INTERNAL_WORKERS_NUMBER = 4;
    private static final int INTERNAL_QUEUE_CAPACITY = 100;

    private final ShardHelper shardHelper = new ShardHelper();
    private final Map<Integer, String> shardToUrl;
    private Integer shard;

    private final ThreadLocal<Map<String, HttpClient>> clients;

    private final ExecutorService executorService = new ThreadPoolExecutor(
            WORKERS_NUMBER,
            WORKERS_NUMBER,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY)
    );

    private final ExecutorService internalRequestService = new ThreadPoolExecutor(
            INTERNAL_WORKERS_NUMBER,
            INTERNAL_WORKERS_NUMBER,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(INTERNAL_QUEUE_CAPACITY)
    );

    public ParallelHttpServer(ServiceConfig config, Object... routers) throws IOException {
        super(createConfigFromPort(config.selfPort()), routers);
        this.shardToUrl = new HashMap<>();

        List<String> sortedUrls = new ArrayList<>(config.clusterUrls());
        sortedUrls.sort(Comparator.naturalOrder());
        for (int i = 0; i < sortedUrls.size(); i++) {
            this.shardToUrl.put(i, sortedUrls.get(i));
            if (sortedUrls.get(i).equals(config.selfUrl())) {
                this.shard = i;
            }
        }

        distributeVNodes(sortedUrls.size());

        this.clients = ThreadLocal.withInitial(() -> {
            Map<String, HttpClient> baseMap = new HashMap<>();
            for (String url : config.clusterUrls()) {
                baseMap.put(url, new HttpClient(new ConnectionString(url)));
            }
            return baseMap;
        });
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (request.getMethod() != Request.METHOD_GET
                && request.getMethod() != Request.METHOD_DELETE
                && request.getMethod() != Request.METHOD_PUT) {
            session.sendResponse(emptyResponse(Response.METHOD_NOT_ALLOWED));
        }

        String id = request.getParameter("id=");

        if (id == null) {
            session.sendResponse(emptyResponse(Response.BAD_REQUEST));
            return;
        }

        int destinationShardId = getShardId(id);

        try {
            if (destinationShardId == shard) {
                executorService.submit(() -> {
                    requestTask(request, session);
                });
            } else {
                internalRequestService.submit(() -> {
                    handleInnerRequest(request, session, destinationShardId);
                });
            }
        } catch (RejectedExecutionException e) {
            session.sendError(
                    Response.SERVICE_UNAVAILABLE,
                    "Handling was rejected due to some internal problem");
        }
    }

    private void handleInnerRequest(Request request, HttpSession session, int shardId) {
        try {
            Request req = new Request(
                    request.getMethod(),
                    request.getURI(),
                    true
            );

            req.setBody(request.getBody());
            Arrays.stream(request.getHeaders())
                    .filter(Objects::nonNull)
                    .forEach(req::addHeader);

            session.sendResponse(
                    clients.get().get(shardToUrl.get(shardId)).invoke(req)
            );
        } catch (Exception e) {
            e.printStackTrace();
            try {
                session.sendError(Response.SERVICE_UNAVAILABLE, "");
            } catch (IOException ex) {
                logger.error(ex.getMessage());
            }
        }
    }

    private int getShardId(String key) {
        return shardHelper.getShardByKey(key);
    }

    private void requestTask(Request request, HttpSession session) {
        try {
            super.handleRequest(request, session);
        } catch (Exception e) {
            handleException(session, e);
        }
    }

    private static void handleException(HttpSession session, Exception e) {
        // ask about it on lecture
        if (e instanceof HttpException) {
            try {
                session.sendResponse(emptyResponse(Response.BAD_REQUEST));
            } catch (IOException ex) {
                e.printStackTrace();
            }
        } else if (e instanceof IOException) {
            try {
                session.sendError(
                        Response.INTERNAL_ERROR,
                        "Handling interrupted by some internal error");
            } catch (IOException ex) {
                logger.error(ex.getMessage());
            }
        }
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        session.sendResponse(emptyResponse(Response.BAD_REQUEST));
    }

    @Override
    public synchronized void stop() {
        executorService.shutdown();
        internalRequestService.shutdown();

        clients.get().values().forEach(Pool::close);

        for (SelectorThread selectorThread : selectors) {
            for (Session session : selectorThread.selector) {
                session.close();
            }
        }

        super.stop();

    }

    private void distributeVNodes(int numberOfServers) {
        int gapPerNode = 2 * (Integer.MAX_VALUE / VNODE_NUMBER_PER_SERVER);
        int gapPerServer = gapPerNode / numberOfServers;

        for (int i = 0; i < numberOfServers; i++) {
            Set<Integer> vnodes = new HashSet<>();
            int currVNode = Integer.MIN_VALUE + i * gapPerServer;
            for (int j = 0; j < VNODE_NUMBER_PER_SERVER; j++) {
                vnodes.add(currVNode);
                currVNode += gapPerNode;
            }
            shardHelper.addShard(i, vnodes);
        }
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }
}
