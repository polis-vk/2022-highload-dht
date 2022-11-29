package ok.dht.test.siniachenko;

import ok.dht.test.siniachenko.hintedhandoff.HintsManager;
import ok.dht.test.siniachenko.service.AsyncEntityService;
import ok.dht.test.siniachenko.range.EntityChunkStreamQueueItem;
import ok.dht.test.siniachenko.service.EntityService;
import ok.dht.test.siniachenko.service.EntityServiceCoordinator;
import ok.dht.test.siniachenko.service.EntityServiceReplica;
import ok.dht.test.siniachenko.range.RangeService;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

public class TycoonHttpServer extends HttpServer {
    private static final Logger LOG = LoggerFactory.getLogger(TycoonHttpServer.class);
    private static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();
    public static final String ENTITY_SERVICE_PATH = "/v0/entity";
    public static final String RANGE_SERVICE_PATH = "/v0/entities";
    public static final String HINTS_PATH = "/v0/hints";
    public static final String REQUEST_TO_REPLICA_HEADER = "Request-to-replica";
    public static final String REPLICA_URL_HEADER = "Replica-url";

    private final ExecutorService executorService;
    private final EntityServiceCoordinator entityServiceCoordinator;
    private final EntityServiceReplica entityServiceReplica;
    private final RangeService rangeService;
    private final HintsManager hintsManager;
    private boolean closed = true;

    public TycoonHttpServer(
        int port,
        ExecutorService executorService,
        EntityServiceCoordinator entityServiceCoordinator,
        EntityServiceReplica entityServiceReplica,
        RangeService rangeService, HintsManager hintsManager) throws IOException {
        super(createHttpConfigFromPort(port));
        this.executorService = executorService;
        this.entityServiceCoordinator = entityServiceCoordinator;
        this.entityServiceReplica = entityServiceReplica;
        this.rangeService = rangeService;
        this.hintsManager = hintsManager;
    }

    private static HttpServerConfig createHttpConfigFromPort(int port) {
        HttpServerConfig httpServerConfig = new HttpServerConfig();
        AcceptorConfig acceptorConfig = new AcceptorConfig();
        acceptorConfig.port = port;
        acceptorConfig.reusePort = true;
        httpServerConfig.acceptors = new AcceptorConfig[]{acceptorConfig};
        httpServerConfig.selectors = AVAILABLE_PROCESSORS;

        return httpServerConfig;
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (ENTITY_SERVICE_PATH.equals(request.getPath())) {
            handleEntityServiceRequest(request, session);
        } else if (RANGE_SERVICE_PATH.equals(request.getPath())) {
            handleRangeServiceRequest(request, session);
        } else if (HINTS_PATH.equals(request.getPath())) {
            handleHintsRequest(request, session);
        } else {
            sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY), session);
        }
    }

    private void handleEntityServiceRequest(Request request, HttpSession session) {
        String idParameter = request.getParameter("id=");
        if (idParameter == null || idParameter.isEmpty()) {
            sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY), session);
        } else {
            if (request.getHeader(REQUEST_TO_REPLICA_HEADER) == null) {
                processRequestAsync(request, session, idParameter, entityServiceCoordinator);
            } else {
                processRequest(request, session, idParameter, entityServiceReplica);
            }
        }
    }

    private void handleRangeServiceRequest(Request request, HttpSession session) {
        String startParameter = request.getParameter("start=");
        String endParameter = request.getParameter("end=");
        if (
            startParameter == null || startParameter.isEmpty()
        ) {
            sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY), session);
        } else if (request.getMethod() != Request.METHOD_GET) {
            sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY), session);
        } else {
            execute(() -> {
                    EntityChunkStreamQueueItem entityChunkStreamQueueItem = rangeService.handleRange(
                        startParameter,
                        endParameter
                    );
                    try {
                        session.write(entityChunkStreamQueueItem);
                    } catch (IOException e) {
                        onSessionException(session, e);
                    }
                },
                session
            );
        }
    }

    private void handleHintsRequest(Request request, HttpSession session) {
        if (request.getMethod() != Request.METHOD_GET) {
            sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY), session);
        } else {
            execute(() -> {
                    String replicaUrl = request.getHeader(REPLICA_URL_HEADER);
                    if (replicaUrl == null) {
                        sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY), session);
                        return;
                    }
                    EntityChunkStreamQueueItem entityChunkStreamQueueItem = hintsManager.getReplicaHintsStream(
                        replicaUrl
                    );
                    try {
                        session.write(entityChunkStreamQueueItem);
                    } catch (IOException e) {
                        onSessionException(session, e);
                        return;
                    }
                    // Only in case of no errors or exception!
                    hintsManager.deleteHintsForReplica(replicaUrl);
                },
                session
            );
        }
    }

    private static void processRequestAsync(
        Request request, HttpSession session, String idParameter, AsyncEntityService entityService
    ) {
        CompletableFuture<Response> responseFuture = switch (request.getMethod()) {
            case Request.METHOD_GET -> entityService.handleGet(request, idParameter);
            case Request.METHOD_PUT -> entityService.handlePut(request, idParameter);
            case Request.METHOD_DELETE -> entityService.handleDelete(request, idParameter);
            default -> CompletableFuture.completedFuture(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
        };
        responseFuture.exceptionally(e -> {
            LOG.error("Error processing async request", e);
            sendResponse(new Response(Response.INTERNAL_ERROR, Response.EMPTY), session);
            return null;
        }).thenAccept(response -> sendResponse(response, session));
    }

    private void processRequest(
        Request request, HttpSession session, String idParameter, EntityService entityService
    ) {
        execute(
            () -> {
                Response response = switch (request.getMethod()) {
                    case Request.METHOD_GET -> entityService.handleGet(request, idParameter);
                    case Request.METHOD_PUT -> entityService.handlePut(request, idParameter);
                    case Request.METHOD_DELETE -> entityService.handleDelete(request, idParameter);
                    default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
                };
                sendResponse(response, session);
            },
            session
        );
    }

    private void execute(Runnable runnable, HttpSession session) {
        try {
            executorService.execute(runnable);
        } catch (RejectedExecutionException e) {
            LOG.error("Cannot execute task", e);
            sendResponse(new Response(Response.INTERNAL_ERROR, Response.EMPTY), session);
        }
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY), session);
    }

    public static void sendResponse(Response response, HttpSession session) {
        try {
            session.sendResponse(response);
        } catch (IOException e1) {
            onSessionException(session, e1);
        }
    }

    private static void onSessionException(HttpSession session, IOException e1) {
        LOG.error("I/O error while sending response", e1);
        try {
            session.close();
        } catch (Exception e2) {
            e2.addSuppressed(e1);
            LOG.error("Exception while closing session", e2);
        }
    }

    @Override
    public synchronized void start() {
        super.start();
        closed = false;
    }

    @Override
    public synchronized void stop() {
        if (!closed) {
            closed = true;
            for (SelectorThread selectorThread : selectors) {
                for (Session session : selectorThread.selector) {
                    session.close();
                }
            }
            super.stop();
        }
    }
}
