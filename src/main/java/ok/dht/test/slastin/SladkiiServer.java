package ok.dht.test.slastin;

import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

public class SladkiiServer extends HttpServer {
    private static final Logger log = LoggerFactory.getLogger(SladkiiServer.class);

    private final SladkiiComponent component;
    private final ExecutorService processors;

    public SladkiiServer(
            HttpServerConfig httpServerConfig,
            SladkiiComponent component,
            ExecutorService processors
    ) throws IOException {
        super(httpServerConfig);
        this.component = component;
        this.processors = processors;
    }

    @Override
    public synchronized void stop() {
        closeAllSessions();
        super.stop();
    }

    private void closeAllSessions() {
        for (var selectorThread : selectors) {
            selectorThread.selector.forEach(Session::close);
        }
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        try {
            processors.submit(() -> handleRequestWrapper(request, session));
        } catch (RejectedExecutionException e) {
            log.error("Can not schedule task for execution", e);
            session.sendResponse(serviceUnavailable());
        }
    }

    private void handleRequestWrapper(Request request, HttpSession session) {
        try {
            super.handleRequest(request, session);
        } catch (Exception e) {
            log.error("Exception occurred inside worker's thread", e);
            sendErrorWrapper(session, e);
        }
    }

    private void sendErrorWrapper(HttpSession session, Exception exception) {
        try {
            session.sendError(Response.BAD_REQUEST, exception.getMessage());
        } catch (IOException e) {
            log.error("Exception occurred while sending error", e);
        }
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        session.sendResponse(badRequest());
    }

    @Path("/v0/entity")
    public Response processRequest(@Param(value = "id", required = true) String id, Request request) {
        if (id.isBlank()) {
            return badRequest();
        }
        return switch (request.getMethod()) {
            case Request.METHOD_GET -> component.get(id);
            case Request.METHOD_PUT -> component.put(id, request);
            case Request.METHOD_DELETE -> component.delete(id);
            default -> badMethod();
        };
    }

    static Response serviceUnavailable() {
        return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
    }

    static Response badMethod() {
        return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
    }

    static Response badRequest() {
        return new Response(Response.BAD_REQUEST, Response.EMPTY);
    }

    static Response internalError() {
        return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
    }

    static Response notFound() {
        return new Response(Response.NOT_FOUND, Response.EMPTY);
    }

    static Response created() {
        return new Response(Response.CREATED, Response.EMPTY);
    }

    static Response accepted() {
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }
}
