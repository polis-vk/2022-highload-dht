package ok.dht.test.frolovm;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.frolovm.artyomdrozdov.BaseEntry;
import ok.dht.test.frolovm.artyomdrozdov.Config;
import ok.dht.test.frolovm.artyomdrozdov.Dao;
import ok.dht.test.frolovm.artyomdrozdov.Entry;
import ok.dht.test.frolovm.artyomdrozdov.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import one.nio.util.Utf8;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ServiceImpl implements Service {

    public static final int FLUSH_THRESHOLD_BYTES = 1_048_576;
    public static final String PATH_ENTITY = "/v0/entity";
    public static final String PARAM_ID_NAME = "id=";
    private static final byte[] BAD_ID = Utf8.toBytes("Given id is bad.");
    private static final byte[] NO_SUCH_METHOD = Utf8.toBytes("No such method.");
    private static final int CORE_POLL_SIZE = 8;
    private static final int MAXIMUM_POLL_SIZE = 64;
    private static final int KEEP_ALIVE_TIME = 5;
    private static final int QUEUE_CAPACITY = 128;
    private final ServiceConfig config;
    private final ExecutorService executorService = new ThreadPoolExecutor(
            CORE_POLL_SIZE,
            MAXIMUM_POLL_SIZE,
            KEEP_ALIVE_TIME,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY)
    );
    private Dao<MemorySegment, Entry<MemorySegment>> dao;
    private HttpServer server;

    public ServiceImpl(ServiceConfig config) {
        this.config = config;
    }

    public ServiceImpl(ServiceConfig config, Dao<MemorySegment, Entry<MemorySegment>> dao) {
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
        }
        server = new HttpServer(createConfigFromPort(config.selfPort())) {

            @Override
            public void handleRequest(Request request, HttpSession session) {
                executorService.submit(
                        () -> {
                            try {
                                if (request.getPath().equals(PATH_ENTITY)) {
                                    Response response = entityHandler(request.getParameter(PARAM_ID_NAME), request);
                                    session.sendResponse(response);
                                } else {
                                    handleDefault(request, session);
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                );
            }

            @Override
            public void handleDefault(Request request, HttpSession session) throws IOException {
                session.sendResponse(emptyResponse(Response.BAD_REQUEST));
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
        server.start();
        server.addRequestHandlers(this);
        return CompletableFuture.completedFuture(null);
    }

    private Response getHandler(String id) {
        Entry<MemorySegment> result = dao.get(stringToSegment(id));
        if (result == null) {
            return emptyResponse(Response.NOT_FOUND);
        } else {
            return new Response(Response.OK, result.value().toByteArray());
        }
    }

    private Response entityHandler(String id, Request request) {
        if (!checkId(id)) {
            return new Response(Response.BAD_REQUEST, BAD_ID);
        }
        switch (request.getMethod()) {
            case Request.METHOD_PUT:
                return putHandler(request, id);
            case Request.METHOD_GET:
                return getHandler(id);
            case Request.METHOD_DELETE:
                return deleteHandler(id);
            default:
                return new Response(Response.BAD_REQUEST, NO_SUCH_METHOD);
        }
    }

    private Response putHandler(Request request, String id) {
        MemorySegment bodySegment = MemorySegment.ofArray(request.getBody());
        dao.upsert(new BaseEntry<>(stringToSegment(id), bodySegment));
        return emptyResponse(Response.CREATED);
    }

    private Response deleteHandler(String id) {
        dao.upsert(new BaseEntry<>(stringToSegment(id), null));
        return emptyResponse(Response.ACCEPTED);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        dao.close();
        dao = null;
        return CompletableFuture.completedFuture(null);
    }

    @ServiceFactory(stage = 1, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
