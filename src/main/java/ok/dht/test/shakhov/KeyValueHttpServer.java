package ok.dht.test.shakhov;

import one.nio.http.HttpException;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class KeyValueHttpServer extends HttpServer {

    private static int processors = Runtime.getRuntime().availableProcessors();

    private final ExecutorService executorService = new ThreadPoolExecutor(processors / 2, processors, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

    public KeyValueHttpServer(HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
    }

    @Override
    public void handleRequest(Request request, HttpSession session) {
        executorService.execute(() -> {
                try {
                    super.handleRequest(request, session);
                } catch (Throwable e) {
//                if (log.isDebugEnabled()) {
//                    log.debug("Bad request", e);
//                }
                    if (e instanceof HttpException) {
                        try {
                            session.sendError(Response.BAD_REQUEST, e.getMessage());
                        } catch (IOException io) {
                            // ignored
                        }
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
    }
}
