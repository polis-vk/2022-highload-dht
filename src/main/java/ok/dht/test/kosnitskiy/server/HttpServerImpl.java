package ok.dht.test.kosnitskiy.server;

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
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class HttpServerImpl extends HttpServer {
    private static final Logger LOG = LoggerFactory.getLogger(HttpServerImpl.class);
    private final ThreadPoolExecutor executor;

    public HttpServerImpl(HttpServerConfig config, ThreadPoolExecutor executor, Object... routers) throws IOException {
        super(config, routers);
        this.executor = executor;
    }

    @Override
    public void handleRequest(Request request, HttpSession session) {
        executor.execute(() -> {
            try {
                super.handleRequest(request, session);
            } catch (IOException e) {
                LOG.error("Couldn't handle the request properly");
            }
        });
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
        session.sendResponse(response);
    }

    @Override
    public synchronized void stop() {
        executor.shutdown();
        boolean isTerminated;
        try {
            isTerminated = executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            isTerminated = false;
            LOG.error("Waiting for tasks to finish timed out");
            Thread.currentThread().interrupt();
        }
        if (!isTerminated) {
            executor.shutdownNow();
        }
        for (SelectorThread selectorThread : selectors) {
            for (Session session : selectorThread.selector) {
                session.scheduleClose();
            }
        }
        super.stop();
    }
}
