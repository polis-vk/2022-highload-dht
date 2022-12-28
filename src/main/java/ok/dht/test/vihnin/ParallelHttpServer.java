package ok.dht.test.vihnin;

import one.nio.http.HttpException;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static ok.dht.test.vihnin.ServiceUtils.emptyResponse;

public class ParallelHttpServer extends HttpServer {

    private static final int WORKERS_NUMBER = 10;
    private static final int QUEUE_CAPACITY = 100;

    private final ExecutorService executorService = new ThreadPoolExecutor(
            WORKERS_NUMBER,
            WORKERS_NUMBER,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY)
    );

    public ParallelHttpServer(HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (request.getMethod() != Request.METHOD_GET
                && request.getMethod() != Request.METHOD_DELETE
                && request.getMethod() != Request.METHOD_PUT) {
            session.sendResponse(emptyResponse(Response.METHOD_NOT_ALLOWED));
        }
        try {
            executorService.submit(() -> {
                requestTask(request, session);
            });
        } catch (RejectedExecutionException e) {
            session.sendError(
                    Response.SERVICE_UNAVAILABLE,
                    "Handling was rejected due to some internal problem");
        }
    }

    private void requestTask(Request request, HttpSession session) {
        try {
            super.handleRequest(request, session);
        } catch (Exception e) {
            handleException(session, e);
        }
    }

    private static void handleException(HttpSession session, Exception e) {
        // ask about it on lecture
        if (e instanceof HttpException) {
            try {
                session.sendResponse(emptyResponse(Response.BAD_REQUEST));
            } catch (IOException ex) {
                e.printStackTrace();
            }
        } else if (e instanceof IOException) {
            try {
                session.sendError(
                        Response.INTERNAL_ERROR,
                        "Handling interrupted by some internal error");
            } catch (IOException ex) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        session.sendResponse(emptyResponse(Response.BAD_REQUEST));
    }

    @Override
    public synchronized void stop() {
        executorService.shutdown();

        for (SelectorThread selectorThread : selectors) {
            for (Session session : selectorThread.selector) {
                session.close();
            }
        }

        super.stop();

    }
}
