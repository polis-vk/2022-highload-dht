package ok.dht.test.komissarov.utils;

import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CustomHttpServer extends HttpServer {

    private static final Logger LOGGER = LogManager.getLogger(CustomHttpServer.class);

    // FIXME hardcoded params
    private final ExecutorService service = new ThreadPoolExecutor(
            4,
            8,
            30,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>()
    );

    public CustomHttpServer(HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
        session.sendResponse(response);
    }

    @Override
    public void handleRequest(Request request, HttpSession session) {
        service.execute(() -> {
            try {
                super.handleRequest(request, session);
            } catch (IOException e) {
                LOGGER.error(e.getMessage());
            }
        });
    }

    @Override
    public synchronized void stop() {
        service.shutdown();
        super.stop();
        for (SelectorThread thread : selectors) {
            thread.selector.forEach(Session::close);
        }
    }
}
