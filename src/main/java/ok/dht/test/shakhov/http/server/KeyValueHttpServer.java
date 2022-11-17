package ok.dht.test.shakhov.http.server;

import ok.dht.test.shakhov.concurrent.DefaultThreadPoolManager;
import ok.dht.test.shakhov.http.HttpUtils;
import ok.dht.test.shakhov.http.stream.StreamAwareSession;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.server.RejectedSessionException;
import one.nio.server.SelectorThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;

import static ok.dht.test.shakhov.http.HttpUtils.ONE_NIO_X_LEADER_TIMESTAMP_HEADER;

public class KeyValueHttpServer extends HttpServer {
    private static final Logger log = LoggerFactory.getLogger(KeyValueHttpServer.class);

    private static final String ID_PARAMETER = "id=";
    private static final String ACK_PARAM = "ack=";
    private static final String FROM_PARAM = "from=";
    private static final String START_PARAMETER = "start=";
    private static final String END_PARAMETER = "end=";

    private static final String ENDPOINT = "/v0/entity";
    private static final Set<Integer> SUPPORTED_METHODS = Set.of(
            Request.METHOD_GET,
            Request.METHOD_PUT,
            Request.METHOD_DELETE
    );
    private static final String STREAM_ENDPOINT = "/v0/entities";
    private static final Set<Integer> STREAM_SUPPORTED_METHODS = Set.of(Request.METHOD_GET);

    private static final int RESPONSE_EXECUTOR_POOL_SIZE = (Runtime.getRuntime().availableProcessors() + 1) / 3;

    private final int clusterSize;
    private final ClientRequestAsyncHandler clientRequestAsyncHandler;
    private final InternalRequestAsyncHandler internalRequestAsyncHandler;
    private final StreamRequestHandler streamRequestHandler;
    private ThreadPoolExecutor responseExecutor;

    public KeyValueHttpServer(HttpServerConfig config,
                              int clusterSize,
                              ClientRequestAsyncHandler clientRequestAsyncHandler,
                              InternalRequestAsyncHandler internalRequestAsyncHandler,
                              StreamRequestHandler streamRequestHandler) throws IOException {
        super(config);
        this.clusterSize = clusterSize;
        this.clientRequestAsyncHandler = clientRequestAsyncHandler;
        this.internalRequestAsyncHandler = internalRequestAsyncHandler;
        this.streamRequestHandler = streamRequestHandler;
    }

    @Override
    public synchronized void start() {
        super.start();
        responseExecutor = DefaultThreadPoolManager.createThreadPool("response", RESPONSE_EXECUTOR_POOL_SIZE);
    }

    @Override
    public void handleRequest(Request request, HttpSession session) {
        String requestPath = request.getPath();
        if (ENDPOINT.equals(requestPath)) {
            handleBasicRequest(request, session);
        } else if (STREAM_ENDPOINT.equals(requestPath)) {
            handleStreamRequest(request, session);
        } else {
            sendResponseAsync(session, HttpUtils.badRequest());
        }
    }

    private void handleStreamRequest(Request request, HttpSession session) {
        if (!STREAM_SUPPORTED_METHODS.contains(request.getMethod())) {
            sendResponseAsync(session, HttpUtils.methodNotAllowed());
            return;
        }

        String start = request.getParameter(START_PARAMETER);
        if (start == null || start.isEmpty()) {
            sendResponseAsync(session, HttpUtils.badRequest());
            return;
        }
        String end = request.getParameter(END_PARAMETER);
        sendResponseAsync(session, streamRequestHandler.handleStreamRequest(request, start, end));
    }

    private void handleBasicRequest(Request request, HttpSession session) {
        if (!SUPPORTED_METHODS.contains(request.getMethod())) {
            sendResponseAsync(session, HttpUtils.methodNotAllowed());
            return;
        }

        String id = request.getParameter(ID_PARAMETER);
        if (id == null || id.isEmpty()) {
            sendResponseAsync(session, HttpUtils.badRequest());
            return;
        }

        String leaderTimestamp = request.getHeader(ONE_NIO_X_LEADER_TIMESTAMP_HEADER);
        if (leaderTimestamp == null || leaderTimestamp.isEmpty()) {
            processClientRequest(request, id, session);
        } else {
            processInternalRequest(request, id, session, leaderTimestamp);
        }
    }

    private void processClientRequest(Request request, String id, HttpSession session) {
        int ack;
        int from;
        try {
            ack = HttpUtils.getIntParameter(request, ACK_PARAM, clusterSize / 2 + 1);
            from = HttpUtils.getIntParameter(request, FROM_PARAM, clusterSize);
        } catch (NumberFormatException e) {
            sendResponseAsync(session, HttpUtils.badRequest());
            return;
        }
        if (ack <= 0 || from > clusterSize || ack > from) {
            sendResponseAsync(session, HttpUtils.badRequest());
            return;
        }

        clientRequestAsyncHandler.handleClientRequestAsync(request, id, ack, from)
                .whenCompleteAsync((Response r, Throwable t) -> {
                            if (t == null && r != null) {
                                sendResponse(session, r);
                            } else {
                                log.error("Unexpected error during processing client {}", request, t);
                                sendResponse(session, HttpUtils.internalError());
                            }
                        },
                        responseExecutor);
    }

    private void processInternalRequest(Request request, String id, HttpSession session, String leaderTimestamp) {
        long parsedLeaderTimestamp;
        try {
            parsedLeaderTimestamp = Long.parseLong(leaderTimestamp);
        } catch (NumberFormatException e) {
            sendResponse(session, HttpUtils.badRequest());
            return;
        }

        internalRequestAsyncHandler.handleInternalRequestAsync(request, id, parsedLeaderTimestamp)
                .whenCompleteAsync((Response r, Throwable t) -> {
                            if (t == null && r != null) {
                                sendResponse(session, r);
                            } else {
                                log.error("Unexpected error during processing internal {}", request, t);
                                sendResponse(session, HttpUtils.internalError());
                            }
                        },
                        responseExecutor);
    }

    private void sendResponseAsync(HttpSession session, Response response) {
        responseExecutor.execute(() -> sendResponse(session, response));
    }

    private static void sendResponse(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e) {
            log.error("Error during sending response", e);
            session.close();
        }
    }

    @Override
    public HttpSession createSession(Socket socket) throws RejectedSessionException {
        return new StreamAwareSession(socket, this);
    }

    @Override
    public synchronized void stop() {
        super.stop();

        DefaultThreadPoolManager.shutdownThreadPool(responseExecutor);

        for (SelectorThread selector : selectors) {
            for (Session session : selector.selector) {
                session.close();
            }
        }
    }
}
