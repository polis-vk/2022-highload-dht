package ok.dht.test.monakhov;

import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AsyncHttpServer extends HttpServer {
    private ExecutorService executor;
    private final AsyncHttpServerConfig config;


    public AsyncHttpServer(AsyncHttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);

        this.config = config;
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        try {
            executor.submit(() -> {
                try {
                    super.handleRequest(request, session);
                } catch (IOException e) {
                    try {
                        session.sendError(Response.INTERNAL_ERROR, e.getMessage());
                    } catch (IOException ex) {
                        // ignore or log, but log is private in Server superclass
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            session.sendError(Response.INTERNAL_ERROR, e.getMessage());
        }
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
        session.sendResponse(response);
    }

    @Override
    public synchronized void start() {
        super.start();

        executor = new ThreadPoolExecutor(
            config.workersNumber,
            config.workersNumber,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(config.queueSize)
        );
    }


    @Override
    public synchronized void stop() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            for (SelectorThread thread : selectors) {
                for (Session session : thread.selector) {
                    session.socket().close();
                }
            }

            super.stop();
        }
    }
}
