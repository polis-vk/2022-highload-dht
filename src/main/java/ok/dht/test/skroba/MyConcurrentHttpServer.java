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
import java.net.http.HttpResponse;
import java.nio.channels.ClosedSelectorException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.lang.Math.max;
import static ok.dht.test.skroba.MyServiceUtils.createDaoFromDir;
import static ok.dht.test.skroba.MyServiceUtils.getEmptyResponse;

public class MyConcurrentHttpServer extends HttpServer {
    public static final String PARENT_REQUEST = "PARENT_REQUEST";
    public static final String TIMESTAMP_REQUEST = "TIMESTAMP_REQUEST";
    public static final String TOMBSTONE = "TOMBSTONE";
    private static final int FLUSH_THRESHOLD_BYTES = 1024 * 1048;
    private static final int CAPACITY_OF_QUEUE = 1024;
    private static final int MAXIMUM_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = max(1, MAXIMUM_POOL_SIZE / 4);
    private static final String ID_PARAMETER = "id=";
    private static final String ACK_PARAMETER = "ack=";
    private static final String FROM_PARAMETER = "from=";
    
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
            if (!pool.awaitTermination(60, TimeUnit.MILLISECONDS)) {
                pool.shutdownNow();
                
                if (!pool.awaitTermination(60, TimeUnit.MILLISECONDS)) {
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
        
        final String id = request.getParameter(ID_PARAMETER);
        
        if (MyServiceUtils.isBadId(id)) {
            session.sendResponse(MyServiceUtils.getEmptyResponse(Response.BAD_REQUEST));
            return;
        }
        
        try {
            requestsWorkers.submit(() -> processRequest(request, session, id));
        } catch (RejectedExecutionException e) {
            LOG.error("Can't submit task: " + e.getMessage());
            session.sendResponse(getEmptyResponse(Response.INTERNAL_ERROR));
        }
    }
    
    private void processRequest(Request request, HttpSession session, String id) {
        try {
            if (request.getHeader(PARENT_REQUEST) != null) {
                long timeStamp = Long.parseLong(request.getHeader(TIMESTAMP_REQUEST).substring(2));
                handleMyself(request, session, id, timeStamp);
                
                return;
            }
            
            List<Integer> parameters = parseParameters(request, session);
            
            if (parameters == null) {
                return;
            }
            
            int from = parameters.get(0);
            int ack = parameters.get(1);
            
            List<String> urls = manager.getUrls(id, from);
            
            long timeStamp = Instant.now().toEpochMilli();
            
            List<CompletableFuture<HttpResponse<byte[]>>> futures = urls
                    .stream()
                    .map(url -> {
                        try {
                            return client.sendRequest(
                                    url + "/v0/entity?id=" + id,
                                    timeStamp,
                                    request.getMethod(),
                                    request.getBody());
                        } catch (URISyntaxException e) {
                            throw new RuntimeException(e);
                        }
                    }).toList();
            
            List<HttpResponse<byte[]>> responses = futures.stream().map(it -> {
                try {
                    return it.get(MyClientImpl.TERMINATION_TIME, MyClientImpl.TIME_UNIT);
                } catch (InterruptedException
                         | ExecutionException
                         | TimeoutException e) {
                    LOG.error("Error while connecting to other node");
                }
                return null;
            }).filter(Objects::nonNull).toList();
            
            final long result = responses
                    .stream()
                    .filter(HttpUtils.PREDICATES.get(request.getMethod()))
                    .count();

            if (ack > result) {
                session.sendResponse(getEmptyResponse(HttpUtils.NOT_ENOUGH_REPLICAS));
                return;
            }
            
            switch (request.getMethod()) {
                case Request.METHOD_GET -> {
                    Optional<Pair<Long, byte[]>> entry = responses.stream()
                            .filter(it -> it.statusCode() != HttpUtils.NOT_FOUND)
                            .map(it -> new Pair<Long, byte[]>(Long.parseLong(
                                        it.headers().allValues(TIMESTAMP_REQUEST).get(0)),
                                    !it.headers().allValues(TOMBSTONE).isEmpty()
                                            ? null
                                            : it.body()
                                    )
                            ).max(Comparator.comparing(Pair::getFirst));
                    
                    if (entry.isEmpty() || entry.get().getSecond() == null) {
                        session.sendResponse(getEmptyResponse(Response.NOT_FOUND));
                        return;
                    }
                    
                    session.sendResponse(new Response(Response.OK, entry.get().getSecond()));
                }
                case Request.METHOD_PUT ->
                        session.sendResponse(getEmptyResponse(Response.CREATED));
                case Request.METHOD_DELETE ->
                        session.sendResponse(getEmptyResponse(Response.ACCEPTED));
                default -> throw new IllegalStateException("Unreachable state");
            }
        } catch (IOException e) {
            LOG.error("IO exception while handling response: " + e.getMessage());
            responseOnIOException(session);
        }
    }
    
    private List<Integer> parseParameters(Request request, HttpSession session) throws IOException {
        final String fromParameter = request.getParameter(FROM_PARAMETER);
        final String ackParameter = request.getParameter(ACK_PARAMETER);
        
        try {
            int from = fromParameter == null ? manager.clusterSize() : Integer.parseInt(fromParameter);
            int ack = ackParameter == null ? (from + 1) / 2 : Integer.parseInt(ackParameter);
            
            if (ack > 0 && ack <= from) {
                return List.of(from, ack);
            }
        } catch (IllegalArgumentException e) {
            //Ignore
        }
        
        session.sendResponse(getEmptyResponse(Response.BAD_REQUEST));
        
        return null;
    }
    
    private void handleMyself(Request request, HttpSession session, String id, long timeStamp) throws IOException {
        switch (request.getMethod()) {
            case Request.METHOD_GET -> handleGet(session, id);
            case Request.METHOD_PUT -> session.sendResponse(upsert(
                    id,
                    MemorySegment.ofArray(request.getBody()),
                    timeStamp,
                    Response.CREATED)
            );
            case Request.METHOD_DELETE -> session.sendResponse(upsert(
                    id,
                    null,
                    timeStamp,
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
        
        Response response = new Response(
                Response.OK,
                entry.value() == null ? Response.EMPTY : entry.value().toByteArray()
        );
        
        response.addHeader(TIMESTAMP_REQUEST + ": " + entry.timeStamp());
        if (entry.isTombstone()) {
            response.addHeader(TOMBSTONE + ": " + "true");
        }
        session.sendResponse(response);
    }
    
    private Response upsert(
            String id,
            MemorySegment value,
            long time,
            String onSuccessStatus
    ) {
        MemorySegment key = MemorySegment.ofArray(Utf8.toBytes(id));
        BaseEntry<MemorySegment> entry = new BaseEntry<>(key, value, time);
        
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
            LOG.error("Socket  already been closed: " + e.getMessage());
        }
    
        try {
            dao.close();
        } catch (IOException e) {
            LOG.error("Can't close dao!");
        }
        
        shutdownAndAwaitTermination(requestsWorkers);
        
        super.stop();
    }
}
