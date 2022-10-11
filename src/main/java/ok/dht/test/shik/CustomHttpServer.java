package ok.dht.test.shik;

import ok.dht.test.shik.workers.WorkersConfig;
import ok.dht.test.shik.workers.WorkersService;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;

public class CustomHttpServer extends HttpServer {

    private static final String CLOSE_CONNECTION_HEADER = "Connection: close";
    private static final Log LOG = LogFactory.getLog(CustomHttpServer.class);
    private static final Set<Integer> SUPPORTED_METHODS = Set.of(
        Request.METHOD_GET,
        Request.METHOD_PUT,
        Request.METHOD_DELETE
    );

    private final WorkersService workersService;

    public CustomHttpServer(HttpServerConfig config,
                            WorkersConfig workersConfig,
                            Object... routers) throws IOException {
        super(config, routers);
        workersService = new WorkersService(workersConfig);
    }

    @Override
    public synchronized void start() {
        workersService.start();
        super.start();
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        try {
            workersService.submitTask(() -> processClientRequest(request, session));
        } catch (RejectedExecutionException e) {
            LOG.warn("Internal executor queue is full", e);
            session.sendResponse(new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY));
        }
    }

    private void processClientRequest(Request request, HttpSession session) {
        try {
            super.handleRequest(request, session);
        } catch (Exception e) {
            LOG.error("Error while processing request: ", e);
            sendErrorToSession(session, e);
        }
    }

    private void sendErrorToSession(HttpSession session, Exception e) {
        try {
            String response = e.getClass() == BufferOverflowException.class
                ? Response.REQUEST_ENTITY_TOO_LARGE
                : Response.BAD_REQUEST;
            session.sendError(response, e.getMessage());
        } catch (IOException e1) {
            LOG.error("Error while sending message about error: ", e1);
        }
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = SUPPORTED_METHODS.contains(request.getMethod())
            ? new Response(Response.BAD_REQUEST, Response.EMPTY)
            : new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
        session.sendResponse(response);
    }

    @Override
    public synchronized void stop() {
        for (SelectorThread selector : selectors) {
            for (Session session : selector.selector) {
                Response response = new Response(Response.OK, Response.EMPTY);
                response.addHeader(CLOSE_CONNECTION_HEADER);
                byte[] responseBytes = response.toBytes(false);
                try {
                    session.write(responseBytes, 0, responseBytes.length);
                } catch (IOException e) {
                    LOG.error("Error while sending client info about closing socket", e);
                }
                session.socket().close();
            }
        }
        super.stop();
        workersService.stop();
    }

}
