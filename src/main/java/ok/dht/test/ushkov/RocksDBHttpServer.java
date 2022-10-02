package ok.dht.test.ushkov;

import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class RocksDBHttpServer extends HttpServer {
    private ExecutorService executor;

    public RocksDBHttpServer(RocksDBHttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
        executor = new ThreadPoolExecutor(
                config.workers,
                config.workers,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(config.queueCapacity)
        );
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        executor.execute(() -> {
            try {
                super.handleRequest(request, session);
            } catch (Exception e) {
                try {
                    session.sendError(Response.BAD_REQUEST, e.getMessage());
                } catch (IOException e1) {
                    // Do nothing
                }
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
        // HttpServer.stop() doesn't close sockets
        for (SelectorThread thread : selectors) {
            for (Session session : thread.selector) {
                session.socket().close();
            }
        }

        super.stop();
    }
}
