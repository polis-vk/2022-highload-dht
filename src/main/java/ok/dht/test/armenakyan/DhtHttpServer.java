package ok.dht.test.armenakyan;

import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

public class DhtHttpServer extends HttpServer {
    private final ExecutorService workerPool;

    public DhtHttpServer(ExecutorService workerPool, HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
        this.workerPool = workerPool;
    }

    @Override
    public void handleRequest(Request request, HttpSession session) {
        workerPool.execute(() -> {
            try {
                super.handleRequest(request, session);
            } catch (Exception e) {
                session.close();
            }
        });
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
    }

    @Override
    public synchronized void stop() {
        for (SelectorThread selectorThread : selectors) {
            if (!selectorThread.selector.isOpen()) {
                continue;
            }
            for (Session session : selectorThread.selector) {
                session.close(); // close open selectors sessions
            }
        }
        super.stop();

        workerPool.shutdownNow();
    }
}
