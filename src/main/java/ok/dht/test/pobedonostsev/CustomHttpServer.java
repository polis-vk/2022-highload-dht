package ok.dht.test.pobedonostsev;

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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CustomHttpServer extends HttpServer {
    private static final Logger LOG = LoggerFactory.getLogger(CustomHttpServer.class);
    private static final int THREAD_COUNT = 64;
    private static final int QUEUE_SIZE = 256;
    private static ExecutorService es;

    public CustomHttpServer(HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
    }

    private static void sendError(HttpSession session, Exception e) {
        try {
            session.sendResponse(new Response(Response.INTERNAL_ERROR, Response.EMPTY));
            LOG.error("Cannot handle", e);
        } catch (IOException ex) {
            LOG.error("Cannot send response", e);
        }
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        try {
            es.execute(() -> handle(request, session));
        } catch (RejectedExecutionException e) {
            sendError(session, e);
        }
    }

    private void handle(Request request, HttpSession session) {
        try {
            super.handleRequest(request, session);
        } catch (Exception e) {
            sendError(session, e);
        }
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
        session.sendResponse(response);
    }

    @Override
    public synchronized void start() {
        es = new ThreadPoolExecutor(THREAD_COUNT, THREAD_COUNT, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_SIZE));
        super.start();
    }

    @Override
    public synchronized void stop() {
        for (SelectorThread selector : selectors) {
            for (Session session : selector.selector) {
                session.close();
            }
        }
        super.stop();
        try {
            es.shutdown();
            boolean terminated = es.awaitTermination(5, TimeUnit.SECONDS);
            if (!terminated) {
                LOG.error("Termination timeout");
            }
        } catch (InterruptedException e) {
            LOG.error("Interrupted while terminating", e);
            Thread.currentThread().interrupt();
        }
    }
}
