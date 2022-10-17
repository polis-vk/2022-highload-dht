package ok.dht.test.dergunov;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.dergunov.database.BaseEntry;
import ok.dht.test.dergunov.database.Entry;
import ok.dht.test.dergunov.database.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.PathMapper;
import one.nio.http.Request;
import one.nio.http.RequestHandler;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;
import one.nio.util.Utf8;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static one.nio.http.Request.METHOD_DELETE;
import static one.nio.http.Request.METHOD_GET;
import static one.nio.http.Request.METHOD_PUT;

public class HttpServerImpl extends HttpServer {

    private static final Log LOGGER = LogFactory.getLog(HttpServerImpl.class);
    private static final String PATH = "/v0/entity";
    private static final String PARAMETER_KEY = "id=";

    private static final int SIZE_QUEUE = 128;
    private static final int COUNT_CORES = 6;

    private static final Set<Integer> SUPPORTED_METHODS = Set.of(METHOD_GET, METHOD_PUT, METHOD_DELETE);
    private static final Response BAD_RESPONSE = new Response(Response.BAD_REQUEST, Response.EMPTY);
    private static final Response METHOD_NOT_ALLOWED = new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
    private static final Response SERVICE_UNAVAILABLE = new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
    private static final Response NOT_FOUND = new Response(Response.NOT_FOUND, Response.EMPTY);
    private final MemorySegmentDao database;
    private final PathMapper handlerMapper = new PathMapper();

    private final ExecutorService poolExecutor = new ThreadPoolExecutor(
            COUNT_CORES,
            COUNT_CORES,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(SIZE_QUEUE)
    );

    public HttpServerImpl(HttpServerConfig config, MemorySegmentDao database, Object... routers) throws IOException {
        super(config, routers);
        this.database = database;
        handlerMapper.add(PATH, new int[]{METHOD_GET}, this::handleGet);
        handlerMapper.add(PATH, new int[]{METHOD_PUT}, this::handlePut);
        handlerMapper.add(PATH, new int[]{METHOD_DELETE}, this::handleDelete);
    }

    private static byte[] toBytes(MemorySegment data) {
        return data == null ? null : data.toByteArray();
    }

    private static MemorySegment fromString(String data) {
        return data == null ? null : MemorySegment.ofArray(Utf8.toBytes(data));
    }

    private static MemorySegment fromBytes(byte[] data) {
        return data == null ? null : MemorySegment.ofArray(data);
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        session.sendResponse(BAD_RESPONSE);
    }

    private static void handleUnavailable(HttpSession session) {
        try {
            session.sendResponse(SERVICE_UNAVAILABLE);
        } catch (IOException ioException) {
            try {
                LOGGER.error("Error when send SERVICE_UNAVAILABLE response", ioException);
                session.close();
            } catch (Exception exception) {
                LOGGER.error("Error when close connection", exception);
            }
        }
    }

    @Override
    public void handleRequest(Request request, HttpSession session) {
        try {
            runHandleRequest(request, session);
        } catch (RejectedExecutionException rejectedExecutionException) {
            LOGGER.error("Reject request", rejectedExecutionException);
            handleUnavailable(session);
        }
    }

    private void runHandleRequest(Request request, HttpSession session) {
        poolExecutor.execute(() -> {
            try {
                String path = request.getPath();
                if (!path.equals(PATH)) {
                    session.sendResponse(BAD_RESPONSE);
                    return;
                }

                int methodName = request.getMethod();
                if (!SUPPORTED_METHODS.contains(methodName)) {
                    session.sendResponse(METHOD_NOT_ALLOWED);
                    return;
                }
                RequestHandler handler = handlerMapper.find(path, methodName);

                if (handler != null) {
                    handler.handleRequest(request, session);
                    return;
                }
                handleDefault(request, session);
            } catch (IOException e) {
                LOGGER.error("Error when send response", e);
                handleUnavailable(session);
            }
        });
    }

    @Override
    public synchronized void stop() {
        for (SelectorThread thread : selectors) {
            for (Session session : thread.selector) {
                session.close();
            }
        }
        super.stop();
        poolExecutor.shutdown();
    }

    private void handleGet(@Nonnull Request request, HttpSession session) throws IOException {
        String entityId = request.getParameter(PARAMETER_KEY, "");
        if (entityId.isEmpty()) {
            session.sendResponse(BAD_RESPONSE);
            return;
        }

        Entry<MemorySegment> result = database.get(fromString(entityId));
        if (result == null) {
            session.sendResponse(NOT_FOUND);
            return;
        }
        session.sendResponse(new Response(Response.OK, toBytes(result.value())));
    }

    private void handlePut(@Nonnull Request request, HttpSession session) throws IOException {
        String entityId = request.getParameter(PARAMETER_KEY, "");
        if (entityId.isEmpty()) {
            session.sendResponse(BAD_RESPONSE);
            return;
        }

        database.upsert(new BaseEntry<>(fromString(entityId), fromBytes(request.getBody())));
        session.sendResponse(new Response(Response.CREATED, Response.EMPTY));
    }

    private void handleDelete(@Nonnull Request request, HttpSession session) throws IOException {
        String entityId = request.getParameter(PARAMETER_KEY, "");
        if (entityId.isEmpty()) {
            session.sendResponse(BAD_RESPONSE);
            return;
        }

        database.upsert(new BaseEntry<>(fromString(entityId), null));
        session.sendResponse(new Response(Response.ACCEPTED, Response.EMPTY));
    }

}
