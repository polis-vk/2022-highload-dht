package ok.dht.test.frolovm;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.drozdov.dao.Config;
import ok.dht.test.drozdov.dao.Entry;
import ok.dht.test.drozdov.dao.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import one.nio.util.Utf8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ServiceImpl implements Service {

    public static final int FLUSH_THRESHOLD_BYTES = 1_048_576;
    public static final String PATH_ENTITY = "/v0/entity";
    public static final String PARAM_ID_NAME = "id=";
    private static final int CORE_POLL_SIZE = Runtime.getRuntime().availableProcessors() * 2;
    private static final int KEEP_ALIVE_TIME = 0;
    private static final int QUEUE_CAPACITY = 128;
    private static final String BAD_ID = "Given id is bad.";
    private static final String NO_SUCH_METHOD = "No such method.";
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceImpl.class);
    private final ServiceConfig config;
    private ExecutorService requestService;

    private MemorySegmentDao dao;
    private HttpServer server;

    public ServiceImpl(ServiceConfig config) {
        this.config = config;
    }

    public ServiceImpl(ServiceConfig config, MemorySegmentDao dao) {
        this.config = config;
        this.dao = dao;
    }

    private static boolean checkId(String id) {
        return id != null && !id.isBlank();
    }

    private static MemorySegment stringToSegment(String value) {
        return MemorySegment.ofArray(Utf8.toBytes(value));
    }

    private static Response emptyResponse(String responseCode) {
        return new Response(responseCode, Response.EMPTY);
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = createAcceptorConfig(port);
        httpConfig.acceptors = new AcceptorConfig[] {acceptor};
        return httpConfig;
    }

    private static AcceptorConfig createAcceptorConfig(int port) {
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        return acceptor;
    }

    private void createDao() throws IOException {
        this.dao = new MemorySegmentDao(new Config(config.workingDir(), FLUSH_THRESHOLD_BYTES));
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        if (dao == null) {
            createDao();
            requestService = new ThreadPoolExecutor(
                    CORE_POLL_SIZE,
                    CORE_POLL_SIZE,
                    KEEP_ALIVE_TIME,
                    TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(QUEUE_CAPACITY)
            );
        }
        server = new HttpServer(createConfigFromPort(config.selfPort())) {
            @Override
            public void handleDefault(Request request, HttpSession session) throws IOException {
                session.sendResponse(emptyResponse(Response.BAD_REQUEST));
            }

            @Override
            public void handleRequest(Request request, HttpSession session) {
                Runnable handleTask = () -> {
                    try {
                        super.handleRequest(request, session);
                    } catch (IOException e) {
                        sessionSendError(session, e);
                    }
                };
                try {
                    requestService.execute(handleTask);
                } catch (RejectedExecutionException exception) {
                    LOGGER.error("If this task cannot be accepted for execution", exception);
                }
            }

            private void sessionSendError(HttpSession session, IOException e) {
                try {
                    session.sendError(Response.BAD_REQUEST, e.getMessage());
                    LOGGER.error("Can't handle request", e);
                } catch (IOException exception) {
                    LOGGER.error("Can't send error message to Bad Request", exception);
                }
            }

            @Override
            public synchronized void stop() {
                closeSessions();
                super.stop();
            }

            private void closeSessions() {
                for (SelectorThread selectorThread : selectors) {
                    selectorThread.selector.forEach(Session::close);
                }
            }
        };
        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    private Response getHandler(String id) {
        Entry result = dao.get(stringToSegment(id));
        if (result == null) {
            return emptyResponse(Response.NOT_FOUND);
        } else {
            return new Response(Response.OK, result.value().toByteArray());
        }
    }

    @Path(PATH_ENTITY)
    public Response entityHandler(@Param(PARAM_ID_NAME) String id, Request request) {
        if (!checkId(id)) {
            return new Response(Response.BAD_REQUEST, Utf8.toBytes(BAD_ID));
        }
        switch (request.getMethod()) {
            case Request.METHOD_PUT:
                return putHandler(request, id);
            case Request.METHOD_GET:
                return getHandler(id);
            case Request.METHOD_DELETE:
                return deleteHandler(id);
            default:
                return new Response(Response.METHOD_NOT_ALLOWED, Utf8.toBytes(NO_SUCH_METHOD));
        }
    }

    private Response putHandler(Request request, String id) {
        MemorySegment bodySegment = MemorySegment.ofArray(request.getBody());
        dao.upsert(new Entry(stringToSegment(id), bodySegment));
        return emptyResponse(Response.CREATED);
    }

    private void closeExecutorPool(ExecutorService pool) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                if (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
                    LOGGER.error("Pool didn't terminate");
                }
            }
        } catch (InterruptedException ie) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private Response deleteHandler(String id) {
        dao.upsert(new Entry(stringToSegment(id), null));
        return emptyResponse(Response.ACCEPTED);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        closeExecutorPool(requestService);
        dao.close();
        dao = null;
        return CompletableFuture.completedFuture(null);
    }

    @ServiceFactory(stage = 2, week = 2, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}