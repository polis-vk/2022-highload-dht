package ok.dht.test.siniachenko;

import ok.dht.test.siniachenko.service.EntityService;
import ok.dht.test.siniachenko.service.EntityServiceCoordinator;
import ok.dht.test.siniachenko.service.EntityServiceReplica;
import one.nio.http.*;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

public class TycoonHttpServer extends HttpServer {
    private static final Logger LOG = LoggerFactory.getLogger(TycoonHttpServer.class);
    private static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();
    public static final String PATH = "/v0/entity";
    public static final String REQUEST_TO_REPLICA_HEADER = "Request-to-replica";

    private final ExecutorService executorService;
    private final EntityServiceCoordinator entityServiceCoordinator;
    private final EntityServiceReplica entityServiceReplica;
    private boolean closed = true;

    public TycoonHttpServer(
        int port,
        ExecutorService executorService,
        EntityServiceCoordinator entityServiceCoordinator,
        EntityServiceReplica entityServiceReplica
    ) throws IOException {
        super(createHttpConfigFromPort(port));
        this.executorService = executorService;
        this.entityServiceCoordinator = entityServiceCoordinator;
        this.entityServiceReplica = entityServiceReplica;
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
        if (!PATH.equals(request.getPath())) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        String idParameter = request.getParameter("id=");
        if (idParameter == null || idParameter.isEmpty()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        execute(session, () -> {
            EntityService entityService;
            entityService = getEntityService(request);
            Response response = switch (request.getMethod()) {
                case Request.METHOD_GET -> entityService.handleGet(request, idParameter);
                case Request.METHOD_PUT -> entityService.handlePut(request, idParameter);
                case Request.METHOD_DELETE -> entityService.handleDelete(request, idParameter);
                default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            };
            sendResponse(session, response);
        });
    }

    private EntityService getEntityService(Request request) {
        if (request.getHeader(REQUEST_TO_REPLICA_HEADER) == null) {
            return entityServiceCoordinator;
        } else {
            return entityServiceReplica;
        }
    }

    private void execute(HttpSession session, Runnable runnable) {
        try {
            executorService.execute(runnable);
        } catch (RejectedExecutionException e) {
            LOG.error("Cannot execute task", e);
            sendResponse(session, new Response(Response.INTERNAL_ERROR, Response.EMPTY));
        }
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        sendResponse(session, new Response(Response.BAD_REQUEST, Response.EMPTY));
    }

    public static void sendResponse(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e1) {
            LOG.error("I/O error while sending response", e1);
            try {
                session.close();
            } catch (Exception e2) {
                e2.addSuppressed(e1);
                LOG.error("Exception while closing session", e2);
            }
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
