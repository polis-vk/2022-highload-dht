package ok.dht.test.skroba;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static ok.dht.test.skroba.MyServiceUtils.getEmptyResponse;

public class MyConcurrentHttpServer extends HttpServer {
    private static final int CAPACITY_OF_QUEUE = 1024;
    private static final int CORE_POOL_SIZE = 4;
    private static final int MAXIMUM_POOL_SIZE = CORE_POOL_SIZE * 2;
    private static final Logger LOG = LoggerFactory.getLogger(MyConcurrentHttpServer.class);
    
    private ExecutorService requestsWorkers;
    
    public MyConcurrentHttpServer(HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
    }
    
    @Override
    public synchronized void start() {
        requestsWorkers = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAXIMUM_POOL_SIZE,
                0L,
                TimeUnit.MILLISECONDS,
                new MyQueue<>(CAPACITY_OF_QUEUE)
        );
        
        super.start();
    }
    
    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        requestsWorkers.submit(() -> {
            try {
                super.handleRequest(request, session);
            } catch (IOException e) {
                LOG.error("Can't handle response: " + e.getMessage());
                
                try {
                    session.sendResponse(getEmptyResponse(Response.INTERNAL_ERROR));
                } catch (IOException ioException) {
                    LOG.error("Can't send error: " + ioException.getMessage());
                }
            }
        });
    }
    
    @Override
    public void handleDefault(
            Request request,
            HttpSession session
    ) throws IOException {
        if (request.getMethod() == Request.METHOD_POST) {
            session.sendResponse(getEmptyResponse(Response.METHOD_NOT_ALLOWED));
            return;
        }
        
        session.sendResponse(getEmptyResponse(Response.BAD_REQUEST));
    }
    
    @Override
    public synchronized void stop() {
        for (SelectorThread thread : selectors) {
            thread.selector.forEach(Session::close);
        }
        
        shutdownAndAwaitTermination(requestsWorkers);
        
        super.stop();
    }
    
    // This code was taken from official documentation, so it's licence allow me to use it.
    private static void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown();
        
        try {
            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                
                if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                    LOG.error("Pool did not terminate");
                }
            }
        } catch (InterruptedException ie) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
