package ok.dht.test.yasevich;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.yasevich.artyomdrozdov.MemorySegmentDao;
import ok.dht.test.yasevich.dao.Config;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ServiceImpl implements Service {

    public static final int ROUTED_REQUEST_TIMEOUT_MS = 30;
    private static final int FLUSH_THRESHOLD = 5 * 1024 * 1024;
    private static final int POOL_QUEUE_SIZE = 1000;
    private static final int FIFO_RARENESS = 3;
    static final Log LOGGER = LogFactory.getLog(ServiceImpl.class);

    private final ServiceConfig serviceConfig;
    private TimeStampingDao timeStampingDao;
    private HttpServer server;

    public ServiceImpl(ServiceConfig config) {
        this.serviceConfig = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        Config daoConfig = new Config(serviceConfig.workingDir(), FLUSH_THRESHOLD);
        timeStampingDao = new TimeStampingDao(new MemorySegmentDao(daoConfig));
        server = new CustomHttpServer(createConfigFromPort(serviceConfig.selfPort()), timeStampingDao);
        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        if (server != null) {
            server.stop();
        }
        if (timeStampingDao != null) {
            timeStampingDao.close();
        }
        return CompletableFuture.completedFuture(null);
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    private class CustomHttpServer extends HttpServer {
        private static final int CPUs = Runtime.getRuntime().availableProcessors();

        private final BlockingQueue<Runnable> queue = new AlmostLifoQueue(POOL_QUEUE_SIZE, FIFO_RARENESS);
        private final ExecutorService workersPool = new ThreadPoolExecutor(CPUs, CPUs, 0L,
                TimeUnit.MILLISECONDS, queue);
        private final ReplicasManager replicasManager;

        public CustomHttpServer(
                HttpServerConfig config,
                Object... routers) throws IOException {

            super(config, routers);
            RandevouzHashingRouter shardingRouter = new RandevouzHashingRouter(serviceConfig.clusterUrls());
            this.replicasManager = new ReplicasManager(timeStampingDao, shardingRouter, serviceConfig.selfUrl());
        }

        @Override
        public void handleRequest(Request request, HttpSession session) throws IOException {
            String key = request.getParameter("id=");
            String innerRequest = request.getParameter("inner=");
            if (innerRequest != null) {
                try {
                    workersPool.execute(() -> {
                        try {
                            Response response = handleInnerRequest(request, key);
                            ReplicasManager.sendResponse(session, response);
                        } catch (IOException e) {
                            ReplicasManager.sendResponse(session,
                                    new Response(Response.INTERNAL_ERROR, Response.EMPTY));
                        }
                    });
                } catch (RejectedExecutionException e) {
                    session.sendResponse(new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY));
                }
                return;
            }

            String replicasParam = request.getParameter("replicas=");
            String ackParam = request.getParameter("ack=");
            String fromParam = request.getParameter("from=");

            String[] replicasParams = replicasParam == null ? null : replicasParam.split("/");

            final int from = replicasParams != null ? Integer.parseInt(replicasParams[1]) :
                    fromParam != null ? Integer.parseInt(fromParam) : serviceConfig.clusterUrls().size();

            final int ack = replicasParams != null ? Integer.parseInt(replicasParams[0]) :
                    ackParam != null ? Integer.parseInt(ackParam) :
                            from / 2 + 1 <= serviceConfig.clusterUrls().size() ? from / 2 + 1 : from;

            try {
                workersPool.execute(() -> {
                    if (!request.getPath().equals("/v0/entity")) {
                        ReplicasManager.sendResponse(session, new Response(Response.BAD_REQUEST, Response.EMPTY));
                        return;
                    }
                    if (key == null || key.isEmpty()) {
                        ReplicasManager.sendResponse(session, new Response(Response.BAD_REQUEST, Response.EMPTY));
                        return;
                    }
                    if (ack > from || ack <= 0) {
                        ReplicasManager.sendResponse(session, new Response(Response.BAD_REQUEST, Response.EMPTY));
                        return;
                    }
                    replicasManager.handleReplicatingRequest(session, request, key, ack, from);
                });
            } catch (RejectedExecutionException e) {
                session.sendResponse(new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY));
            }
        }

        private Response handleInnerRequest(Request request, String id) throws IOException {
            return switch (request.getMethod()) {
                case Request.METHOD_GET -> handleGet(id);
                case Request.METHOD_PUT -> handlePut(id, request);
                case Request.METHOD_DELETE -> handleDelete(id);
                default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            };
        }

        private Response handleGet(String id) throws IOException {
            TimeStampingDao.TimeStampedValue entry = timeStampingDao.get(id);
            if (entry == null) {
                return new Response(Response.NOT_FOUND, Response.EMPTY);
            }
            if (entry.value == null) {
                return new Response(Response.NOT_FOUND, entry.timeBytes());
            }
            return new Response(Response.OK, entry.wholeToBytes());
        }

        private Response handleDelete(String id) {
            timeStampingDao.upsertTimeStamped(id, null);
            return new Response(Response.ACCEPTED, Response.EMPTY);
        }

        private Response handlePut(String id, Request request) {
            timeStampingDao.upsertTimeStamped(id, request.getBody());
            return new Response(Response.CREATED, Response.EMPTY);
        }

        @Override
        public synchronized void stop() {
            for (SelectorThread thread : selectors) {
                if (!thread.selector.isOpen()) {
                    continue;
                }
                for (Session session : thread.selector) {
                    session.socket().close();
                }
            }
            super.stop();
            workersPool.shutdown();
        }

    }

    @ServiceFactory(stage = 4, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }

}
