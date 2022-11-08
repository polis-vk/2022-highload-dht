package ok.dht.test.panov;

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
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.stream.StreamSupport;

public class ConcurrentHttpServer extends HttpServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentHttpServer.class);

    ExecutorService executorService;

    public ConcurrentHttpServer(HttpServerConfig config, ExecutorService executorService, Object... routers) throws IOException {
        super(config, routers);
        this.executorService = executorService;
    }

    @Override
    public synchronized void start() {
        super.start();
    }

    @Override
    public synchronized void stop() {
        cleanup.shutdown();

        Arrays.stream(selectors)
                .filter(SelectorThread::isAlive)
                .flatMap(selectorThread -> StreamSupport.stream(selectorThread.selector.spliterator(), false))
                .forEach(Session::close);

        super.stop();
    }

    @Override
    public void handleRequest(Request request, HttpSession session) {
        executorService.execute(() -> {
            try {
                super.handleRequest(request, session);
            } catch (Exception e) {
                LOGGER.warn(e.getMessage());
                try {
                    session.sendError(Response.BAD_REQUEST, e.getMessage());
                } catch (IOException ioE) {
                    LOGGER.error("Request handling has failed", e);
                }
            }
        });
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
    }
}
