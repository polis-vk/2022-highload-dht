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

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class HttpServerImpl extends HttpServer {

    private static final String PATH = "/v0/entity";
    private static final String PARAMETER_KEY = "id=";

    private static final int SIZE_QUEUE = 128;
    private static final int COUNT_CORES = 6;
    private static final Response BAD_RESPONSE = new Response(Response.BAD_REQUEST, Response.EMPTY);
    private static final Response NOT_FOUND = new Response(Response.NOT_FOUND, Response.EMPTY);
    private final MemorySegmentDao database;
    private final PathMapper handlerMapper = new PathMapper();

    private final ExecutorService poolExecutor = new ThreadPoolExecutor(
            COUNT_CORES,
            COUNT_CORES,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(SIZE_QUEUE)
    );

    public HttpServerImpl(HttpServerConfig config, MemorySegmentDao database, Object... routers) throws IOException {
        super(config, routers);
        this.database = database;
        handlerMapper.add(PATH, new int[]{Request.METHOD_GET}, this::handleGet);
        handlerMapper.add(PATH, new int[]{Request.METHOD_PUT}, this::handlePut);
        handlerMapper.add(PATH, new int[]{Request.METHOD_DELETE}, this::handleDelete);
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

    @Override
    public void handleRequest(Request request, HttpSession session) {
        poolExecutor.execute(() -> {
            int methodName = request.getMethod();
            String path = request.getPath();

            RequestHandler handler = handlerMapper.find(path, methodName);
            try {
                if (handler != null) {
                    handler.handleRequest(request, session);
                    return;
                }
                handleDefault(request, session);
            } catch (IOException e) {
                throw new RuntimeException(e);
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
