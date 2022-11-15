package ok.dht.test.ilin.servers;

import ok.dht.test.ilin.config.ExpandableHttpServerConfig;
import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ExpandableHttpServer extends HttpServer {
    private final ExecutorService executorService;
    private final Logger logger = LoggerFactory.getLogger(ExpandableHttpServer.class);

    public ExpandableHttpServer(ExpandableHttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(config.queueCapacity);
        this.executorService = new ThreadPoolExecutor(
            config.workers,
            config.workers,
            0L,
            TimeUnit.MILLISECONDS,
            queue
        );
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        switch (request.getMethod()) {
            case Request.METHOD_GET, Request.METHOD_PUT, Request.METHOD_DELETE ->
                session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            default -> session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
        }
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        try {
            executorService.execute(() -> {
                try {
                    super.handleRequest(request, session);
                } catch (Exception e) {
                    logger.error("failed to handle request: {}", e.getMessage());
                    sendBadRequest(session);
                }
            });
        } catch (RejectedExecutionException e) {
            logger.error("Failed to run execution: {}", e.getMessage());
            sendServiceUnavailable(session);
        }
    }

    private void sendServiceUnavailable(HttpSession session) {
        try {
            session.sendResponse(new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY));
        } catch (IOException e) {
            logger.error("Failed to send response: {}", e.getMessage());
        }
    }

    private void sendBadRequest(HttpSession session) {
        try {
            session.sendError(Response.BAD_REQUEST, "failed to execute request.");
        } catch (IOException e) {
            logger.error("failed to send error: {}", e.getMessage());
        }
    }

    @Override
    public synchronized void stop() {
        Arrays.stream(selectors).forEach(it -> it.selector.forEach(Session::close));
        super.stop();
    }
}
