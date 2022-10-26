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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

public class ConcurrentHttpServer extends HttpServer {

    private static final int CORE_POOL_SIZE = 4;
    private static final int MAXIMUM_POOL_SIZE = 8;
    private static final long KEEP_ALIVE_TIME = 0;
    private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentHttpServer.class);

    private ExecutorService executorService;

    public ConcurrentHttpServer(HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
    }

    @Override
    public synchronized void start() {
        executorService = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAXIMUM_POOL_SIZE,
                KEEP_ALIVE_TIME,
                TimeUnit.MICROSECONDS,
                new ArrayBlockingQueue<>(64)
        );

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
