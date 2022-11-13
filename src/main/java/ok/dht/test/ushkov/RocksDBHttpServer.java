package ok.dht.test.ushkov;

import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class RocksDBHttpServer extends HttpServer {
    private final ExecutorService executor;
    private static final Logger LOG = LoggerFactory.getLogger(RocksDBHttpServer.class);

    public RocksDBHttpServer(RocksDBHttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
        int processors = Runtime.getRuntime().availableProcessors();
        int workers = config.workers >= 0 ? config.workers : processors;
        executor = new ThreadPoolExecutor(
                workers,
                workers,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(config.queueCapacity)
        );
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        try {
            executor.execute(() -> doHandleRequest(request, session));
        } catch (RejectedExecutionException e) {
            LOG.info("drop request, queue is full");
            session.sendError(Response.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    private void doHandleRequest(Request request, HttpSession session) {
        try {
            super.handleRequest(request, session);
        } catch (Exception e) {
            handleException(session, e);
        }
    }

    private void handleException(HttpSession session, Exception e) {
        try {
            session.sendError(Response.BAD_REQUEST, e.getMessage());
        } catch (IOException e1) {
            LOG.error("could not send response", e1);
        }
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
        session.sendResponse(response);
    }

    @Override
    public synchronized void stop() {
        // HttpServer.stop() doesn't close sockets
        for (SelectorThread thread : selectors) {
            for (Session session : thread.selector) {
                session.socket().close();
            }
        }
        executor.shutdown();
        super.stop();
    }

    public boolean awaitStop(long timeout, TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }
}
