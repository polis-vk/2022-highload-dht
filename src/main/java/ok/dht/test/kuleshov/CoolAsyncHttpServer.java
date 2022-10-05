package ok.dht.test.kuleshov;

import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CoolAsyncHttpServer extends CoolHttpServer {
    private static final int CORE_POOL_SIZE = 8;
    private static final int MAXIMUM_POOL_SIZE = 8;
    private ExecutorService executorService;

    public CoolAsyncHttpServer(HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
    }

    @Override
    public synchronized void start() {
        executorService = new ThreadPoolExecutor(CORE_POOL_SIZE,
                MAXIMUM_POOL_SIZE,
                100,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(128)
        );

        super.start();
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        executorService.execute(() -> {
            try {
                super.handleRequest(request, session);
            } catch (Exception e) {
                try {
                    session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    @Override
    public synchronized void stop() {
        boolean isFinished;
        try {
            isFinished = executorService.awaitTermination(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new RuntimeException(e);
        }

        if (isFinished) {
            executorService.shutdown();
        }

        super.stop();
    }
}
