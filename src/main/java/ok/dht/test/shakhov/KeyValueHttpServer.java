package ok.dht.test.shakhov;

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
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static ok.dht.test.shakhov.HttpUtils.ACK_PARAM;
import static ok.dht.test.shakhov.HttpUtils.FROM_PARAM;
import static ok.dht.test.shakhov.HttpUtils.ID_PARAMETER;
import static ok.dht.test.shakhov.HttpUtils.ONE_NIO_X_LEADER_TIMESTAMP_HEADER;
import static ok.dht.test.shakhov.HttpUtils.badRequest;
import static ok.dht.test.shakhov.HttpUtils.methodNotAllowed;

public class KeyValueHttpServer extends HttpServer {
    private static final Logger log = LoggerFactory.getLogger(KeyValueHttpServer.class);

    private static final String ENDPOINT = "/v0/entity";
    private static final Set<Integer> SUPPORTED_METHODS = Set.of(Request.METHOD_GET, Request.METHOD_PUT, Request.METHOD_DELETE);

    private static final int QUEUE_MAX_SIZE = 50_000;
    private static final int MAX_POOL_SIZE = Runtime.getRuntime().availableProcessors() / 2;
    private static final int CORE_POOL_SIZE = MAX_POOL_SIZE / 2;
    private static final int KEEP_ALIVE_TIME_SECONDS = 30;
    private static final RejectedExecutionHandler DISCARD_POLICY = new ThreadPoolExecutor.DiscardPolicy();
    private static final int AWAIT_TERMINATION_TIMEOUT_SECONDS = 20;

    private final int clusterSize;
    private final ClientRequestHandler clientRequestHandler;
    private final InternalRequestHandler internalRequestHandler;
    private ExecutorService executorService;

    public KeyValueHttpServer(HttpServerConfig config,
                              int clusterSize,
                              ClientRequestHandler clientRequestHandler,
                              InternalRequestHandler internalRequestHandler) throws IOException
    {
        super(config);
        this.clusterSize = clusterSize;
        this.clientRequestHandler = clientRequestHandler;
        this.internalRequestHandler = internalRequestHandler;
    }

    @Override
    public synchronized void start() {
        super.start();
        BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>(QUEUE_MAX_SIZE);
        executorService = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_TIME_SECONDS,
                TimeUnit.SECONDS,
                taskQueue,
                DISCARD_POLICY
        );
    }

    @Override
    public void handleRequest(Request request, HttpSession session) {
        executorService.execute(() -> {
            try {
                session.sendResponse(processRequest(request));
            } catch (IOException e) {
                log.error("Error during sending response", e);
                session.close();
            }
        });
    }

    private Response processRequest(Request request) {
        if (!ENDPOINT.equals(request.getPath())) {
            return badRequest();
        }

        if (!SUPPORTED_METHODS.contains(request.getMethod())) {
            return methodNotAllowed();
        }

        String id = request.getParameter(ID_PARAMETER);
        if (id == null || id.isEmpty()) {
            return badRequest();
        }

        String leaderTimestamp = request.getHeader(ONE_NIO_X_LEADER_TIMESTAMP_HEADER);
        if (leaderTimestamp == null || leaderTimestamp.isEmpty()) {
            int ack;
            int from;
            try {
                ack = HttpUtils.getIntParameter(request, ACK_PARAM, clusterSize / 2 + 1);
                from = HttpUtils.getIntParameter(request, FROM_PARAM, clusterSize);
            } catch (NumberFormatException e) {
                return badRequest();
            }
            if (ack <= 0 || from > clusterSize || ack > from) {
                return badRequest();
            }
            return clientRequestHandler.handleClientRequest(request, id, ack, from);
        } else {
            long parsedLeaderTimestamp;
            try {
                parsedLeaderTimestamp = Long.parseLong(leaderTimestamp);
            } catch (NumberFormatException e) {
                return badRequest();
            }
            return internalRequestHandler.handleInternalRequest(request, id, parsedLeaderTimestamp);
        }
    }

    @Override
    public synchronized void stop() {
        super.stop();

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(AWAIT_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                executorService.awaitTermination(AWAIT_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        for (SelectorThread selector : selectors) {
            for (Session session : selector.selector) {
                session.close();
            }
        }
    }
}
