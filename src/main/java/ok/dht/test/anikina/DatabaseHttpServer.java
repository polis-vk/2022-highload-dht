package ok.dht.test.anikina;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.anikina.dao.BaseEntry;
import ok.dht.test.anikina.dao.Config;
import ok.dht.test.anikina.dao.Entry;
import ok.dht.test.anikina.dao.MemorySegmentDao;
import ok.dht.test.anikina.utils.MemorySegmentUtils;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DatabaseHttpServer extends HttpServer {
    private static final Log log = LogFactory.getLog(DatabaseHttpServer.class);

    private static final int THREADS_MIN = 8;
    private static final int THREAD_MAX = 10;
    private static final int MAX_QUEUE_SIZE = 128;
    private static final int TERMINATION_TIMEOUT_MS = 800;
    private static final long FLUSH_THRESHOLD_BYTES = 4 * 1024 * 1024;

    private final ExecutorService executorService =
            new ThreadPoolExecutor(
                    THREADS_MIN,
                    THREAD_MAX,
                    0,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(MAX_QUEUE_SIZE),
                    new ThreadPoolExecutor.AbortPolicy()
            );
    private final MemorySegmentDao dao;

    public DatabaseHttpServer(ServiceConfig config) throws IOException {
        super(createHttpServerConfig(config.selfPort()));
        this.dao = new MemorySegmentDao(
                new Config(config.workingDir(), FLUSH_THRESHOLD_BYTES));
    }

    private static HttpServerConfig createHttpServerConfig(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptorConfig = new AcceptorConfig();
        acceptorConfig.port = port;
        acceptorConfig.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptorConfig};
        return httpConfig;
    }

    @Override
    public void handleRequest(Request request, final HttpSession session) {
        executorService.execute(() -> {
            try {
                session.sendResponse(processRequest(request));
            } catch (IOException e) {
                if (log.isDebugEnabled()) {
                    log.debug(e.getMessage());
                }
            }
        });
    }

    @Override
    public synchronized void stop() {
        for (SelectorThread selectorThread : selectors) {
            for (Session session : selectorThread.selector) {
                session.close();
            }
        }
        super.stop();
    }

    public void close() throws IOException {
        stop();

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(TERMINATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }

        dao.close();
    }

    private Response processRequest(Request request) {
        String key = request.getParameter("id=");
        if (!request.getPath().equals("/v0/entity") || key == null || key.isEmpty()) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }

        switch (request.getMethod()) {
            case Request.METHOD_GET -> {
                byte[] value = getFromDao(key);
                if (value == null) {
                    return new Response(Response.NOT_FOUND, Response.EMPTY);
                }
                return new Response(Response.OK, value);
            }
            case Request.METHOD_PUT -> {
                insertIntoDao(key, request.getBody());
                return new Response(Response.CREATED, Response.EMPTY);
            }
            case Request.METHOD_DELETE -> {
                insertIntoDao(key, null);
                return new Response(Response.ACCEPTED, Response.EMPTY);
            }
            default -> {
                return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            }
        }
    }

    private byte[] getFromDao(String key) {
        Entry<MemorySegment> entry = dao.get(MemorySegmentUtils.fromString(key));
        return entry == null ? null : MemorySegmentUtils.toBytes(entry.value());
    }

    private void insertIntoDao(String key, byte[] bytes) {
        dao.upsert(new BaseEntry<>(
                MemorySegmentUtils.fromString(key),
                MemorySegmentUtils.fromBytes(bytes))
        );
    }
}
