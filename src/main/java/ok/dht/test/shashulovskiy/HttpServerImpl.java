package ok.dht.test.shashulovskiy;

import ok.dht.test.shashulovskiy.sharding.Shard;
import ok.dht.test.shashulovskiy.sharding.ShardingManager;
import one.nio.http.*;
import one.nio.net.Session;
import one.nio.server.SelectorThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class HttpServerImpl extends HttpServer {

    private static final Logger LOG = LoggerFactory.getLogger(HttpServerImpl.class);

    private static final int MAXIMUM_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = Math.max(1, MAXIMUM_POOL_SIZE / 4);

    private final ExecutorService requestHandlerPool;

    public HttpServerImpl(HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);

        this.requestHandlerPool = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAXIMUM_POOL_SIZE,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingStack<>()
        );
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        try {
            requestHandlerPool.submit(() -> {
                try {
                    super.handleRequest(request, session);
                } catch (IOException e) {
                    // TODO HANDLE RESPONSE
                    LOG.error("IO Exception occurred while processing request: " + e.getMessage(), e);
                }
            });
        } catch (RejectedExecutionException e) {
            LOG.warn("Request rejected", e);
            session.sendResponse(new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY));
        } catch (Throwable e) {
            // TODO REMOVE
            System.out.println("Error!" + e.getMessage());
        }
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
        session.sendResponse(response);
    }

    @Override
    public synchronized void stop() {
        for (SelectorThread selector : selectors) {
            selector.selector.forEach(Session::close);
        }

        super.stop();

        Utils.shutdownAndAwaitTermination(requestHandlerPool);
    }
}
