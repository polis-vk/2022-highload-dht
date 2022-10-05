package ok.dht.test.yasevich;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.yasevich.artyomdrozdov.MemorySegmentDao;
import ok.dht.test.yasevich.dao.BaseEntry;
import ok.dht.test.yasevich.dao.Config;
import ok.dht.test.yasevich.dao.Dao;
import ok.dht.test.yasevich.dao.Entry;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ServiceImpl implements Service {

    private static final int FLUSH_THRESHOLD = 5 * 1024 * 1024;
    private static final int POOL_QUEUE_SIZE = 1000;
    private static final int FIFO_RARENESS = 3;

    private final ServiceConfig config;
    private Dao<MemorySegment, Entry<MemorySegment>> dao;
    private HttpServer server;

    public ServiceImpl(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        dao = new MemorySegmentDao(new Config(config.workingDir(), FLUSH_THRESHOLD));
        server = new CustomHttpServer(createConfigFromPort(config.selfPort()), dao, true);
        server.start();
        server.addRequestHandlers(this);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        if (server != null) {
            server.stop();
        }
        if (dao != null) {
            dao.close();
        }
        return CompletableFuture.completedFuture(null);
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    private static MemorySegment fromString(String data) {
        return MemorySegment.ofArray(data.toCharArray());
    }

    private static class CustomHttpServer extends HttpServer {
        private static final int CPUs = Runtime.getRuntime().availableProcessors();

        private final boolean rejectWhenOverloaded;
        private final Dao<MemorySegment, Entry<MemorySegment>> dao;
        private final BlockingQueue<Runnable> queue = new AlmostLifoQueue(POOL_QUEUE_SIZE, FIFO_RARENESS);
        private final ExecutorService pool = new ThreadPoolExecutor(CPUs, CPUs, 0L, TimeUnit.MILLISECONDS, queue);


        public CustomHttpServer(HttpServerConfig config, Dao<MemorySegment, Entry<MemorySegment>> dao, boolean rejectWhenOverloaded, Object... routers) throws IOException {
            super(config, routers);
            this.rejectWhenOverloaded = rejectWhenOverloaded;
            this.dao = dao;
        }

        @Override
        public void handleDefault(Request request, HttpSession session) throws IOException {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
        }

        @Override
        public void handleRequest(Request request, HttpSession session) throws IOException {
            String id = request.getParameter("id");
            if (id == null || id.isEmpty() || id.equals("=")) {
                session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                return;
            }
            if (rejectWhenOverloaded && this.isOverloaded()) {
                session.sendResponse(new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY));
                return;
            }
            pool.execute(() -> {
                Response response = switch (request.getMethod()) {
                    case Request.METHOD_GET -> handleGet(id);
                    case Request.METHOD_PUT -> handlePost(id, request);
                    case Request.METHOD_DELETE -> handleDelete(id);
                    default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
                };
                try {
                    session.sendResponse(response);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        private boolean isOverloaded() {
            return queue.size() >= POOL_QUEUE_SIZE;
        }

        private Response handleGet(String id) {
            Entry<MemorySegment> entry;
            try {
                entry = dao.get(fromString(id));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (entry == null) {
                return new Response(Response.NOT_FOUND, Response.EMPTY);
            }
            return new Response(Response.OK, entry.value().toByteArray());
        }

        private Response handleDelete(String id) {
            dao.upsert(new BaseEntry<>(fromString(id), null));
            return new Response(Response.ACCEPTED, Response.EMPTY);
        }

        private Response handlePost(String id, Request request) {
            dao.upsert(new BaseEntry<>(fromString(id), MemorySegment.ofArray(request.getBody())));
            return new Response(Response.CREATED, Response.EMPTY);
        }

        @Override
        public synchronized void stop() {
            for (SelectorThread thread : selectors) {
                for (Session session : thread.selector) {
                    session.socket().close();
                }
            }
            super.stop();
            pool.shutdown();
        }

    }

    @ServiceFactory(stage = 2, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }

}
