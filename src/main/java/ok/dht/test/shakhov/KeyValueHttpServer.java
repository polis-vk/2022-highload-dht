package ok.dht.test.shakhov;

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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class KeyValueHttpServer extends HttpServer {
    private static final Logger log = LoggerFactory.getLogger(KeyValueHttpServer.class);

    private ExecutorService executorService;

    public KeyValueHttpServer(HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
    }

    @Override
    public synchronized void start() {
        super.start();
        int maximumPoolSize = Runtime.getRuntime().availableProcessors();
        int corePoolSize = maximumPoolSize / 2;
        BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();
        executorService = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, 60, TimeUnit.SECONDS, taskQueue);
    }

    @Override
    public void handleRequest(Request request, HttpSession session) {
        executorService.execute(() -> {
            try {
                super.handleRequest(request, session);
            } catch (Exception e) {
                try {
                    session.sendError(Response.BAD_REQUEST, e.getMessage());
                } catch (IOException ioEx) {
                    log.error("IO Error occurred", ioEx);
                }
            }
        });
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response badRequest = new Response(Response.BAD_REQUEST, Response.EMPTY);
        session.sendResponse(badRequest);
    }

    @Override
    public synchronized void stop() {
        super.stop();
        for (SelectorThread selector : selectors) {
            for (Session session : selector.selector) {
                session.close();
            }
        }

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(20, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                executorService.awaitTermination(10, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
