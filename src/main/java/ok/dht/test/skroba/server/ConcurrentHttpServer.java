package ok.dht.test.skroba.server;

import ok.dht.test.lutsenko.service.LinkedBlockingStack;
import ok.dht.test.skroba.db.EntityDao;
import ok.dht.test.skroba.db.exception.DaoException;
import ok.dht.test.skroba.server.util.AcceptedPaths;
import ok.dht.test.skroba.server.util.handlers.RequestHandler;
import ok.dht.test.skroba.shard.Manager;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ConcurrentHttpServer extends HttpServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentHttpServer.class);
    
    private static final int MAXIMUM_POOL_SIZE = Runtime.getRuntime()
            .availableProcessors();
    private static final int CORE_POOL_SIZE = Math.max(1, MAXIMUM_POOL_SIZE / 6);
    private static final int QUEUE_SIZE = 2048;
    
    private static final String ID_PARAMETER = "id=";
    
    private static final Set<String> ACCEPTED_PATHS = Arrays.stream(AcceptedPaths.values())
            .map(AcceptedPaths::getPath)
            .collect(
                    Collectors.toSet());
    private static final Set<Integer> SUPPORTED_METHODS = Set.of(
            Request.METHOD_GET,
            Request.METHOD_PUT,
            Request.METHOD_DELETE
    );
    private final EntityDao dao;
    
    private final Map<String, RequestHandler> handlers;
    private ExecutorService requestWorkers;
    
    public ConcurrentHttpServer(final EntityDao dao, final Manager manager, final HttpServerConfig config,
                                final Object... routers)
            throws IOException {
        super(config, routers);
        this.dao = dao;
        handlers = Arrays.stream(AcceptedPaths.values())
                .collect(Collectors.toMap(AcceptedPaths::getPath,
                        it -> it.getHandler(manager, dao)));
    }
    
    @Override
    public void handleRequest(final Request request, final HttpSession session) throws IOException {
        if (!ACCEPTED_PATHS.contains(request.getPath())) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }
        
        if (!SUPPORTED_METHODS.contains(request.getMethod())) {
            session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
            return;
        }
        
        final String id = request.getParameter(ID_PARAMETER);
        
        if (id == null || id.isBlank()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }
        
        try {
            requestWorkers.submit(() -> processRequest(request, session, id));
        } catch (RejectedExecutionException e) {
            LOGGER.error("Can't submit task: " + e.getMessage());
            session.sendResponse(new Response(Response.INTERNAL_ERROR));
        }
    }
    
    private void processRequest(final Request request, final HttpSession session, final String id) {
        final RequestHandler handler = handlers.get(request.getPath());
        
        try {
            handler.handle(request, session, id);
        } catch (DaoException e) {
            LOGGER.warn("Exception occurred while working with dao: " + e.getMessage());
            
            exceptionSender(session);
        } catch (IOException e) {
            exceptionSender(session);
        }
    }
    
    public void exceptionSender(final HttpSession session) {
        try {
            session.sendResponse(new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY));
        } catch (IOException e) {
            LOGGER.error("Unable respond!");
        }
    }
    
    @Override
    public synchronized void start() {
        dao.open();
        
        requestWorkers = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAXIMUM_POOL_SIZE,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingStack<>(QUEUE_SIZE)
        );
        
        super.start();
    }
    
    @Override
    public synchronized void stop() {
        for (SelectorThread selector : selectors) {
            if (selector.selector.isOpen()) {
                selector.selector.forEach(Session::close);
            }
        }
        
        super.stop();
        
        shutdownAndAwaitTermination(requestWorkers);
        
        dao.close();
    }
    
    // This code was taken from official documentation, so it's licence allow me to use it.
    private static void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown();
        
        try {
            if (!pool.awaitTermination(60, TimeUnit.MILLISECONDS)) {
                pool.shutdownNow();
                
                if (!pool.awaitTermination(60, TimeUnit.MILLISECONDS)) {
                    LOGGER.error("Pool did not terminate");
                }
            }
        } catch (InterruptedException ie) {
            pool.shutdownNow();
            Thread.currentThread()
                    .interrupt();
        }
    }
}
