package ok.dht.test.ushkov.http;

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

public abstract class AsyncHttpServer extends HttpServer {
    private final ExecutorService executor;
    protected static final Logger LOG = LoggerFactory.getLogger(AsyncHttpServer.class);

    public AsyncHttpServer(AsyncHttpServerConfig config, Object... routers) throws IOException {
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
    public final void handleRequest(Request request, HttpSession session) throws IOException {
        try {
            executor.execute(() -> handleRequestAsync(request, session));
        } catch (RejectedExecutionException e) {
            LOG.info("drop request, queue is full");
            session.sendError(Response.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    protected abstract void handleRequestAsync(Request request, HttpSession session);

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
