package ok.dht.test.kurdyukov.server;

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
import java.io.UncheckedIOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public class HttpServerAsync extends HttpServer {
    private static final Logger logger = LoggerFactory.getLogger(HttpServerAsync.class);
    private static final int AWAIT_TERMINATE_SECONDS = 1;

    private final ExecutorService executorService;

    public HttpServerAsync(
            HttpServerConfig config,
            ExecutorService executorService,
            Object... routers
    ) throws IOException {
        super(config, routers);
        this.executorService = executorService;
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        asyncHandleRequest(request, session);
    }

    @Override
    public synchronized void stop() {
        for (SelectorThread thread : selectors) {
            thread.selector.forEach(Session::close);
        }

        super.stop();
        executorService.shutdown();

        try {
            if (executorService.awaitTermination(AWAIT_TERMINATE_SECONDS, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("Fail stopping thread pool workers", e);
            Thread.currentThread().interrupt();
        }
    }

    private void asyncHandleRequest(
            Request request,
            HttpSession session
    ) throws IOException {
        try {
            executorService.execute(() -> doExecute(request, session));
        } catch (RejectedExecutionException e) {
            logger.warn("Reject request", e);
            session.sendResponse(new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY));
        }
    }

    private void doExecute(Request request, HttpSession session) {
        try {
            super.handleRequest(request, session);
        } catch (IOException e) {
            logger.error("Handling request exception", e);
            throw new UncheckedIOException(e);
        }
    }
}
