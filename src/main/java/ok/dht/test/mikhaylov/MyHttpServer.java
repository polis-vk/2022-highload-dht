package ok.dht.test.mikhaylov;

import one.nio.http.HttpException;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class MyHttpServer extends HttpServer {
    private final ExecutorService requestHandlers;

    private static final int REQUEST_HANDLERS = 4;
    private static final int MAX_REQUESTS = 128;

    public MyHttpServer(HttpServerConfig config) throws IOException {
        super(config);
        requestHandlers = new ThreadPoolExecutor(
                REQUEST_HANDLERS,
                REQUEST_HANDLERS,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingDeque<>(MAX_REQUESTS)
        );
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        try {
            requestHandlers.submit(() -> handleRequestImpl(request, session));
        } catch (RejectedExecutionException ignored) {
            session.sendError(Response.SERVICE_UNAVAILABLE, "Server is overloaded");
        }
    }

    private void handleRequestImpl(Request request, HttpSession session) {
        try {
            super.handleRequest(request, session);
        } catch (Exception e) {
            handleRequestException(e, session);
        }
    }

    private static void handleRequestException(Exception e, HttpSession session) {
        try {
            // missing required parameter
            if (e instanceof HttpException && e.getCause() instanceof NoSuchElementException) {
                session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            } else {
                session.sendError(Response.INTERNAL_ERROR, e.getMessage());
            }
        } catch (IOException ex) {
            RuntimeException re = new RuntimeException(ex);
            re.addSuppressed(e);
            throw re;
        }
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
    }

    @Override
    public synchronized void stop() {
        requestHandlers.shutdownNow();
        for (SelectorThread selectorThread : selectors) {
            for (Session session : selectorThread.selector) {
                session.close();
            }
        }
        super.stop();
    }
}
