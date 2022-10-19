package ok.dht.test.skroba;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.skroba.client.MyClient;
import ok.dht.test.skroba.client.MyClientImpl;
import ok.dht.test.skroba.dao.MemorySegmentDao;
import ok.dht.test.skroba.dao.base.BaseEntry;
import ok.dht.test.skroba.dao.base.Entry;
import ok.dht.test.skroba.shard.Manager;
import ok.dht.test.skroba.shard.MyManagerImpl;
import ok.dht.test.skroba.shard.Node;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;
import one.nio.util.Utf8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.channels.ClosedSelectorException;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.max;
import static ok.dht.test.skroba.MyServiceUtils.createDaoFromDir;
import static ok.dht.test.skroba.MyServiceUtils.getEmptyResponse;

public class MyConcurrentHttpServer extends HttpServer {
    private static final int FLUSH_THRESHOLD_BYTES = 1024 * 1048;
    private static final int CAPACITY_OF_QUEUE = 1024;
    private static final int MAXIMUM_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = max(1, MAXIMUM_POOL_SIZE / 4);
    
    private static final Set<String> SUPPORTED_PATHS = Set.of(
            "/v0/entity"
    );
    private static final Set<Integer> SUPPORTED_METHODS = Set.of(
            Request.METHOD_GET,
            Request.METHOD_PUT,
            Request.METHOD_DELETE
    );
    private static final Logger LOG = LoggerFactory.getLogger(MyConcurrentHttpServer.class);
    
    private final MyClient client = new MyClientImpl();
    private final ServiceConfig config;
    private ExecutorService requestsWorkers;
    private MemorySegmentDao dao;
    private Manager manager;
    
    public MyConcurrentHttpServer(
            ServiceConfig serviceConfig,
            HttpServerConfig config,
            Object... routers
    ) throws IOException {
        super(config, routers);
        this.manager = new MyManagerImpl(serviceConfig);
        this.dao = createDaoFromDir(serviceConfig.workingDir(), FLUSH_THRESHOLD_BYTES);
        this.config = serviceConfig;
    }
    
    private static void responseOnIOException(HttpSession session) {
        try {
            session.sendResponse(getEmptyResponse(Response.INTERNAL_ERROR));
        } catch (IOException ioException) {
            LOG.error("Can't send error: " + ioException.getMessage());
        }
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
    public void handleRequest(final Request request, final HttpSession session) throws IOException {
        if (!SUPPORTED_PATHS.contains(request.getPath())) {
            session.sendResponse(MyServiceUtils.getEmptyResponse(Response.BAD_REQUEST));
            return;
        }
        
        if (!SUPPORTED_METHODS.contains(request.getMethod())) {
            session.sendResponse(MyServiceUtils.getEmptyResponse(Response.METHOD_NOT_ALLOWED));
            return;
        }
        
        final String id = request.getParameter("id=");
        
        if (MyServiceUtils.isBadId(id)) {
            session.sendResponse(MyServiceUtils.getEmptyResponse(Response.BAD_REQUEST));
            return;
        }
        
        try {
            requestsWorkers.execute(() -> processRequest(request, session, id));
        } catch (RejectedExecutionException e) {
            LOG.error("Can't submit task: " + e.getMessage());
            session.sendResponse(getEmptyResponse(Response.INTERNAL_ERROR));
        }
    }
    
    private void processRequest(Request request, HttpSession session, String id) {
        try {
            Node node = manager.getUrlById(id);
            
            if (node.getUrl().equals(config.selfUrl())) {
                handleMyself(request, session, id);
            } else {
                delegateHandling(node.getUrl(), request, session, id);
            }
        } catch (IOException e) {
            LOG.error("IO exception while handling response: " + e.getMessage());
            responseOnIOException(session);
        }
    }
    
    private void delegateHandling(String url, Request request, HttpSession session, String id) throws IOException {
        try {
            client.sendRequest(url + "/v0/entity?id=" + id, request.getMethod(), request.getBody()).handleAsync(
                    (response, throwable) -> {
                        try {
                            if (throwable == null) {
                                session.sendResponse(
                                        new Response(
                                                Integer.toString(response.statusCode()),
                                                response.body()));
                                return null;
                            }
                            
                            session.sendResponse(MyServiceUtils.getEmptyResponse(Response.INTERNAL_ERROR));
                        } catch (IOException e) {
                            LOG.error("Can't send response!" + e.getMessage());
                            throw new RuntimeException(e);
                        }
                        return null;
                    }
            );
        } catch (URISyntaxException e) {
            LOG.error("Wrong url: " + e.getMessage());
            session.sendResponse(MyServiceUtils.getEmptyResponse(Response.INTERNAL_ERROR));
        }
    }
    
    private void handleMyself(Request request, HttpSession session, String id) throws IOException {
        switch (request.getMethod()) {
            case Request.METHOD_GET -> handleGet(session, id);
            case Request.METHOD_PUT -> session.sendResponse(upsert(
                    id,
                    MemorySegment.ofArray(request.getBody()),
                    Response.CREATED)
            );
            case Request.METHOD_DELETE -> session.sendResponse(upsert(
                    id,
                    null,
                    Response.ACCEPTED
            ));
            default -> throw new IllegalStateException("Unreachable state");
        }
    }
    
    private void handleGet(HttpSession session, String id) throws IOException {
        Entry<MemorySegment> entry = dao.get(
                MemorySegment.ofArray(Utf8.toBytes(id))
        );
        
        if (entry == null) {
            session.sendResponse(MyServiceUtils.getResponse(
                    Response.NOT_FOUND,
                    "There's no entity with id: " + id
            ));
            return;
        }
        
        session.sendResponse(new Response(
                Response.OK,
                entry.value().toByteArray()
        ));
    }
    
    private Response upsert(
            String id,
            MemorySegment value,
            String onSuccessStatus
    ) {
        MemorySegment key = MemorySegment.ofArray(Utf8.toBytes(id));
        BaseEntry<MemorySegment> entry = new BaseEntry<>(key, value);
        
        try {
            dao.upsert(entry);
        } catch (RuntimeException err) {
            LOG.error("Error while upsert operation:\n " + err);
            return MyServiceUtils.getEmptyResponse(Response.INTERNAL_ERROR);
        }
        
        return MyServiceUtils.getEmptyResponse(onSuccessStatus);
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
        try {
            for (SelectorThread thread : selectors) {
                thread.selector.forEach(Session::close);
            }
        } catch (ClosedSelectorException e) {
            LOG.error("Socket already been closed: " + e.getMessage());
        }
        
        shutdownAndAwaitTermination(requestsWorkers);
        
        try {
            dao.close();
        } catch (IOException e) {
            LOG.error("Can't close dao!");
        }
        
        super.stop();
    }
}
