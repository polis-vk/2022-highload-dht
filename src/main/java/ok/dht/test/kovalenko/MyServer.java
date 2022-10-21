package ok.dht.test.kovalenko;

import ok.dht.test.kovalenko.dao.utils.PoolKeeper;
import ok.dht.test.kovalenko.shards.MyServerBase;
import ok.dht.test.kovalenko.shards.MyServiceBase;
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
import java.net.http.HttpTimeoutException;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MyServer extends HttpServer {
    private static final Set<Integer /*HTTP-method id*/> availableMethods
            = Set.of(Request.METHOD_GET, Request.METHOD_PUT, Request.METHOD_DELETE);
    private static final int N_WORKERS = 2 * (Runtime.getRuntime().availableProcessors() + 1);
    private static final int QUEUE_CAPACITY = 10 * N_WORKERS;
    private final Logger log = LoggerFactory.getLogger(MyServerBase.class);
    private final PoolKeeper workers;

    public MyServer(HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
        this.workers = new PoolKeeper(
                new ThreadPoolExecutor(1, N_WORKERS,
                        60, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                        new ThreadPoolExecutor.AbortPolicy()),
                3 * 60);
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = MyServiceBase.emptyResponseForCode(Response.BAD_REQUEST);
        session.sendResponse(response);
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (!MyServer.availableMethods.contains(request.getMethod())) {
            Response response = MyServiceBase.emptyResponseForCode(Response.METHOD_NOT_ALLOWED);
            session.sendResponse(response);
            return;
        }
        String id = request.getParameter("id=");
        if (id == null || id.isEmpty()) {
            Response response = MyServiceBase.emptyResponseForCode(Response.BAD_REQUEST);
            session.sendResponse(response);
            return;
        }
        try {
            workers.submit(() -> handle(request, session));
        } catch (RejectedExecutionException e) { // AbortPolicy
            sendError(Response.GATEWAY_TIMEOUT, e, session);
        } // No other exceptions may be risen
    }

    @Override
    public synchronized void stop() {
        workers.close();
        for (SelectorThread selectorThread : selectors) {
            if (selectorThread.selector.isOpen()) {
                for (Session session : selectorThread.selector) {
                    session.close();
                }
            }
        }
        super.stop();
    }

    private void handle(Request request, HttpSession session) {
        try {
            super.handleRequest(request, session);
        } catch (HttpTimeoutException e) {
            sendError(Response.SERVICE_UNAVAILABLE, e, session);
        } catch (Exception ex) {
            sendError(Response.INTERNAL_ERROR, ex, session);
        }
    }

    private void sendError(String responseCode, Exception e, HttpSession session) {
        try {
            log.error("Unexpected error", e);
            session.sendError(responseCode, e.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected error, unable to send error", ex);
            session.close();
        }
    }
}
