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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ServiceImpl implements Service {

    static final int ROUTED_REQUEST_TIMEOUT_MS = 100;
    static final Log LOGGER = LogFactory.getLog(ServiceImpl.class);
    static final String COORDINATOR_TIMESTAMP_HEADER = "timestamp-from-coordinator";
    private static final int FLUSH_THRESHOLD = 1024 * 1024;
    private static final int POOL_QUEUE_SIZE = 100;

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

    static void sendResponse(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (Exception e) {
            ServiceImpl.LOGGER.error("Error when sending " + response.getStatus());
            session.close();
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

    private class CustomHttpServer extends HttpServer {
        private static final int CPUs = Runtime.getRuntime().availableProcessors();

        private final ExecutorService workersPool = new ThreadPoolExecutor(CPUs, CPUs, 0L,
                TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(POOL_QUEUE_SIZE));
        private final ReplicasManager replicasManager = new ReplicasManager(timeStampingDao,
                new RandevouzHashingRouter(serviceConfig.clusterUrls()), serviceConfig.selfUrl());

        public CustomHttpServer(
                HttpServerConfig config,
                Object... routers) throws IOException {
            super(config, routers);
        }

        @Override
        public void handleRequest(Request request, HttpSession session) throws IOException {
            String key = request.getParameter("id=");
            String coordinatorTimestamp = request.getHeader(COORDINATOR_TIMESTAMP_HEADER + ':');
            if (coordinatorTimestamp != null) {
                try {
                    workersPool.execute(() -> {
                        try {
                            long time = Long.parseLong(coordinatorTimestamp);
                            Response response = handleInnerRequest(request, key, time);
                            sendResponse(session, response);
                        } catch (NumberFormatException e) {
                            sendResponse(session, new Response(Response.BAD_REQUEST, Response.EMPTY));
                        } catch (IOException e) {
                            sendResponse(session, new Response(Response.INTERNAL_ERROR, Response.EMPTY));
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
            int from;
            int ack;
            try {
                from = replicasParams != null ? Integer.parseInt(replicasParams[1]) :
                        fromParam != null ? Integer.parseInt(fromParam) : serviceConfig.clusterUrls().size();
                ack = replicasParams != null ? Integer.parseInt(replicasParams[0]) :
                        ackParam != null ? Integer.parseInt(ackParam) :
                                from / 2 + 1 <= serviceConfig.clusterUrls().size() ? from / 2 + 1 : from;
            } catch (NumberFormatException e) {
                sendResponse(session, new Response(Response.BAD_REQUEST, Response.EMPTY));
                return;
            }

            try {
                workersPool.execute(() -> {
                    if (!request.getPath().equals("/v0/entity")
                            || key == null || key.isEmpty()
                            || ack > from || ack <= 0) {
                        sendResponse(session, new Response(Response.BAD_REQUEST, Response.EMPTY));
                        return;
                    }
                    long time = System.currentTimeMillis();
                    replicasManager.handleReplicatingRequest(session, request, key, time, ack, from);
                });
            } catch (RejectedExecutionException e) {
                workersPool.execute(() ->
                        sendResponse(session, new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY)));
            }
        }

        private Response handleInnerRequest(Request request, String id, long time) throws IOException {
            return switch (request.getMethod()) {
                case Request.METHOD_GET -> handleGet(id);
                case Request.METHOD_PUT -> handlePut(id, request, time);
                case Request.METHOD_DELETE -> handleDelete(id, time);
                default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            };
        }

        private Response handleGet(String id) {
            TimeStampingDao.TimeStampedValue entry = timeStampingDao.get(id);
            if (entry == null) {
                return new Response(Response.NOT_FOUND, Response.EMPTY);
            }
            if (entry.value == null) {
                return new Response(Response.NOT_FOUND, entry.timeBytes());
            }
            return new Response(Response.OK, entry.wholeToBytes());
        }

        private Response handleDelete(String id, long time) {
            timeStampingDao.upsert(id, null, time);
            return new Response(Response.ACCEPTED, Response.EMPTY);
        }

        private Response handlePut(String id, Request request, long time) {
            timeStampingDao.upsert(id, request.getBody(), time);
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
            workersPool.shutdown();
            super.stop();
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
