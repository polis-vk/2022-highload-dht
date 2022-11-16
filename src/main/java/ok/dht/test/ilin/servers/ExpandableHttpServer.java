package ok.dht.test.ilin.servers;

import ok.dht.test.ilin.config.ExpandableHttpServerConfig;
import ok.dht.test.ilin.domain.Headers;
import ok.dht.test.ilin.domain.ReplicasInfo;
import ok.dht.test.ilin.hashing.impl.ConsistentHashing;
import ok.dht.test.ilin.replica.ReplicasHandler;
import ok.dht.test.ilin.service.EntityService;
import ok.dht.test.ilin.session.ExpandableHttpSession;
import ok.dht.test.ilin.sharding.ShardHandler;
import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.server.RejectedSessionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ExpandableHttpServer extends HttpServer {
    private final ExecutorService executorService;
    private final ReplicasHandler replicasHandler;
    private static final Logger logger = LoggerFactory.getLogger(ExpandableHttpServer.class);
    private final int nodesSize;
    private final EntityService entityService;

    public ExpandableHttpServer(
        EntityService entityService,
        ReplicasHandler replicasHandler,
        int nodesSize,
        ExpandableHttpServerConfig config,
        Object... routers
    ) throws IOException {
        super(config, routers);
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(config.queueCapacity);
        this.executorService = new ThreadPoolExecutor(
            config.workers,
            config.workers,
            0L,
            TimeUnit.MILLISECONDS,
            queue
        );
        this.entityService = entityService;
        this.replicasHandler = replicasHandler;
        this.nodesSize = nodesSize;
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        switch (request.getMethod()) {
            case Request.METHOD_GET, Request.METHOD_PUT, Request.METHOD_DELETE -> sendBadRequest(session);
            default -> session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
        }
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        switch (request.getPath()) {
            case "/v0/entities" -> handleEntities(request, session);
            case "/v0/entity" -> handleEntity(request, session);
            default -> handleDefault(request, session);
        }
    }

    @Override
    public HttpSession createSession(Socket socket) throws RejectedSessionException {
        return new ExpandableHttpSession(socket, this);
    }

    private void handleEntities(Request request, HttpSession session) throws IOException {
        if (request.getMethod() != Request.METHOD_GET) {
            session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
        }
        String start = request.getParameter("start=");
        String end = request.getParameter("end=");
        if (start == null) {
            sendBadRequest(session);
            return;
        }
        executorService.execute(() -> {
            entityService.listEntries(session, start, end);
        });
    }

    private void handleEntity(Request request, HttpSession session) throws IOException {
        String key = request.getParameter("id=");
        if (key == null || key.isEmpty()) {
            handleDefault(request, session);
            return;
        }

        String ack = request.getParameter("ack=");
        String from = request.getParameter("from=");

        ReplicasInfo replicasInfo;
        try {
            if (ack == null || from == null) {
                replicasInfo = new ReplicasInfo(nodesSize);
            } else {
                replicasInfo = new ReplicasInfo(Integer.parseInt(ack), Integer.parseInt(from));
            }
        } catch (NumberFormatException e) {
            sendBadRequest(session);
            return;
        }

        if (replicasInfo.ack() > replicasInfo.from() || replicasInfo.ack() == 0) {
            sendBadRequest(session);
            return;
        }

        String timestamp = request.getHeader(Headers.TIMESTAMP_HEADER);
        final boolean isController = timestamp == null;
        if (isController) {
            request.addHeader(Headers.TIMESTAMP_HEADER + System.currentTimeMillis());
        }

        try {
            executorService.execute(() -> {
                CompletableFuture<Response> result = !isController ? replicasHandler.selfExecute(
                    key,
                    request
                ) : replicasHandler.execute(key, replicasInfo, request);
                result.whenCompleteAsync((response, throwable) -> {
                    if (throwable != null) {
                        logger.error("Failed to self execute request: {}", throwable.getMessage());
                        sendInternalError(session);
                    } else {
                        try {
                            session.sendResponse(response);
                        } catch (IOException e) {
                            logger.error("failed to send response: {}", e.getMessage());
                        }
                    }
                });
            });
        } catch (RejectedExecutionException e) {
            logger.error("Failed to run execution: {}", e.getMessage());
            sendServiceUnavailable(session);
        }
    }

    public static void sendServiceUnavailable(HttpSession session) {
        try {
            session.sendResponse(new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY));
        } catch (IOException e) {
            logger.error("Failed to send response: {}", e.getMessage());
        }
    }

    public static void sendBadRequest(HttpSession session) {
        try {
            session.sendError(Response.BAD_REQUEST, "failed to execute request.");
        } catch (IOException e) {
            logger.error("failed to send error: {}", e.getMessage());
        }
    }

    public static void sendInternalError(HttpSession session) {
        try {
            session.sendResponse(new Response(Response.INTERNAL_ERROR, Response.EMPTY));
        } catch (IOException e) {
            logger.error("Failed to send response: {}", e.getMessage());
        }
    }

    @Override
    public synchronized void stop() {
        Arrays.stream(selectors).forEach(it -> {
            if (it.selector.isOpen()) {
                it.selector.forEach(Session::close);
            }
        });
        super.stop();
    }
}
