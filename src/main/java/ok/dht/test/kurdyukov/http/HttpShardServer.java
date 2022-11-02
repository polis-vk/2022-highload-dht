package ok.dht.test.kurdyukov.http;

import ok.dht.test.kurdyukov.dao.model.DaoEntry;
import ok.dht.test.kurdyukov.dao.repository.DaoRepository;
import ok.dht.test.kurdyukov.sharding.Sharding;
import ok.dht.test.kurdyukov.utils.ObjectMapper;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.channels.ClosedSelectorException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class HttpShardServer extends HttpServer {
    private static final Logger logger = LoggerFactory.getLogger(HttpShardServer.class);
    private static final int AWAIT_TERMINATE_SECONDS = 1;
    private static final int OK = 200;
    private static final int CREATED = 201;
    private static final int ACCEPTED = 202;
    private static final int NOT_FOUND = 404;
    private static final Set<Integer> supportMethods = Set
            .of(Request.METHOD_GET, Request.METHOD_PUT, Request.METHOD_DELETE);
    private static final String ENDPOINT = "/v0/entity";
    private static final String NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";

    private final int clusterSize;
    private final HttpClientDao clientDao = new HttpClientDao();
    private final DaoRepository daoRepository;
    private final ExecutorService executorService;
    private final Sharding sharding;

    public HttpShardServer(HttpServerConfig config, List<String> urls, DaoRepository daoRepository,
                           ExecutorService executorService, Sharding sharding, Object... routers) throws IOException {
        super(config, routers);
        this.clusterSize = urls.size();
        this.daoRepository = daoRepository;
        this.executorService = executorService;
        this.sharding = sharding;
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (!request.getPath().equals(ENDPOINT)) {
            session.sendResponse(responseEmpty(Response.BAD_REQUEST));
            return;
        }

        int method = request.getMethod();
        if (!supportMethods.contains(method)) {
            session.sendResponse(responseEmpty(Response.METHOD_NOT_ALLOWED));
            return;
        }

        String id = request.getParameter("id=");
        if (id == null || id.isBlank()) {
            session.sendResponse(responseEmpty(Response.BAD_REQUEST));
            return;
        }

        try {
            executorService.execute(() -> doExecute(request, session, method, id));
        } catch (RejectedExecutionException e) {
            logger.warn("Reject request", e);
            session.sendResponse(responseEmpty(Response.SERVICE_UNAVAILABLE));
        }
    }

    @Override
    public synchronized void stop() {
        try {
            for (SelectorThread thread : selectors) {
                thread.selector.forEach(Session::close);
            }
        } catch (ClosedSelectorException e) {
            logger.error("Sockets were closed.", e);
        }

        super.stop();
        executorService.shutdown();

        try {
            if (executorService.awaitTermination(AWAIT_TERMINATE_SECONDS, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("Fail stopping thread pool workers", e);
            Thread.currentThread().interrupt();
        }

        daoRepository.close();
    }

    private void doExecute(Request request, HttpSession session, int method, String id) {
        try {
            boolean fromClusterRequest = request.getHeader(HttpClientDao.CLUSTER_HEADER) != null;

            if (fromClusterRequest) {
                handleRequestInCluster(request, session, method, id);
                return;
            }

            String fromParam = request.getParameter("from=");
            String ackParam = request.getParameter("ack=");

            int from;
            int ack;
            try {
                from = (fromParam != null) ? Integer.parseInt(fromParam) : clusterSize;
                ack = (ackParam != null) ? Integer.parseInt(ackParam) : (from + 1) / 2;
            } catch (NumberFormatException e) {
                session.sendResponse(responseEmpty(Response.BAD_REQUEST));
                return;
            }

            if (ack <= 0 || ack > from) {
                session.sendResponse(responseEmpty(Response.BAD_REQUEST));
                return;
            }

            final var urlsNode = sharding.getClusterUrlsByCount(id, from);
            final var timestamp = Instant.now();
            final var okResponses = new AtomicInteger(0);
            final var handleResponses = new AtomicInteger(0);
            final var multicastWaitingFutures = new ConcurrentHashMap<Integer, CompletableFuture<HttpResponse<byte[]>>>();
            final var resultDaoEntry = new AtomicReference<DaoEntry>();

            for (int i = 0; i < urlsNode.size(); i++) {
                final var future = clientDao.requestNode(
                        String.format("%s%s%s", urlsNode.get(i), ENDPOINT, "?id=" + id),
                        method,
                        timestamp,
                        request.getBody()
                );
                final var curI = i;

                multicastWaitingFutures.put(curI, future);
                future.handleAsync(
                        (response, throwable) -> {
                            try {
                                if (throwable != null) {
                                    logger.error("Fail send response!", throwable);
                                    handleNotEnoughReplicas(session, from, ack, okResponses, handleResponses);
                                    return null;
                                }

                                multicastWaitingFutures.remove(curI);

                                switch (request.getMethod()) {
                                    case Request.METHOD_GET -> {
                                        if (response.statusCode() == OK) {
                                            DaoEntry newEntry = ObjectMapper.deserialize(response.body());

                                            while (true) {
                                                DaoEntry oldEntry = resultDaoEntry.get();

                                                if (oldEntry != null && oldEntry.compareTo(newEntry) >= 0
                                                        || resultDaoEntry.compareAndSet(oldEntry, newEntry)) {
                                                    break;
                                                }
                                            }
                                        }

                                        var curOk = okResponses.incrementAndGet();
                                        var last = resultDaoEntry.get();

                                        if (curOk == ack) {
                                            if (last == null || last.isTombstone) {
                                                session.sendResponse(responseEmpty(Response.NOT_FOUND));
                                            } else {
                                                session.sendResponse(new Response(Response.ok(last.value)));
                                            }
                                            multicastWaitingFutures.forEach((k, v) -> v.cancel(false));
                                        }
                                    }
                                    case Request.METHOD_PUT -> {
                                        if (response.statusCode() == CREATED) {
                                            var curOk = okResponses.incrementAndGet();

                                            if (curOk == ack) {
                                                session.sendResponse(responseEmpty(Response.CREATED));
                                                multicastWaitingFutures.forEach((k, v) -> v.cancel(false));
                                            }
                                        }
                                    }
                                    case Request.METHOD_DELETE -> {
                                        if (response.statusCode() == ACCEPTED) {
                                            var curOk = okResponses.incrementAndGet();

                                            if (curOk == ack) {
                                                session.sendResponse(responseEmpty(Response.ACCEPTED));
                                                multicastWaitingFutures.forEach((k, v) -> v.cancel(false));
                                            }
                                        }
                                    }
                                    default ->
                                            throw new IllegalArgumentException("Unsupported request method: " + method);
                                }
                                handleNotEnoughReplicas(session, from, ack, okResponses, handleResponses);
                            } catch (IOException | ClassNotFoundException e) {
                                logger.error("Fail send response!", e);
                            }

                            return null;
                        }
                );
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void handleNotEnoughReplicas(
            HttpSession session,
            int from,
            int ack,
            AtomicInteger okResponses,
            AtomicInteger handleResponses
    ) throws IOException {
        var currentSnapshot = handleResponses.incrementAndGet();
        var curOk = okResponses.get();

        if (currentSnapshot == from && curOk < ack) {
            session.sendResponse(responseEmpty(NOT_ENOUGH_REPLICAS));
        }
    }
    private void handleRequestInCluster(
            Request request,
            HttpSession session,
            int method,
            String id
    ) throws IOException {
        Instant timestamp = Instant.parse(request.getHeader(HttpClientDao.TIMESTAMP_HEADER).substring(2));

        switch (method) {
            case Request.METHOD_GET -> {
                byte[] entry = daoRepository.get(id);

                if (entry == null) {
                    session.sendResponse(responseEmpty(Response.NOT_FOUND));
                } else {
                    session.sendResponse(new Response(Response.ok(entry)));
                }
            }
            case Request.METHOD_PUT -> {
                DaoEntry daoEntry = new DaoEntry(timestamp, request, false);

                daoRepository.put(id, ObjectMapper.serialize(daoEntry));
                session.sendResponse(responseEmpty(Response.CREATED));
            }
            case Request.METHOD_DELETE -> {
                DaoEntry daoEntry = new DaoEntry(timestamp, request, true);

                daoRepository.put(id, ObjectMapper.serialize(daoEntry));
                session.sendResponse(responseEmpty(Response.ACCEPTED));
            }
            default -> throw new IllegalArgumentException("Unsupported request method: " + method);
        }
    }

    private static Response responseEmpty(String status) {
        return new Response(status, Response.EMPTY);
    }
}
