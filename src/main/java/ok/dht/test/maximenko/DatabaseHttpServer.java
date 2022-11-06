package ok.dht.test.maximenko;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.maximenko.dao.BaseEntry;
import ok.dht.test.maximenko.dao.Config;
import ok.dht.test.maximenko.dao.Dao;
import ok.dht.test.maximenko.dao.Entry;
import ok.dht.test.maximenko.dao.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class DatabaseHttpServer extends HttpServer {
    static final int flushDaoThresholdBytes = 10000000;
    private final static String request_path = "/v0/entity";
    static final private Response badRequest = new Response(
            String.valueOf(HttpURLConnection.HTTP_BAD_REQUEST),
            Response.EMPTY
    );
    private Dao dao;
    private ExecutorService requestHandlers;

    private final java.nio.file.Path workDir;
    public DatabaseHttpServer(HttpServerConfig config, java.nio.file.Path workDir) throws IOException {
        super(config);
        this.workDir = workDir;
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (!request.getPath().equals(request_path)) {
            session.sendResponse(badRequest);
            return;
        }

        String key = request.getParameter("id=");
        if (key == null || key.equals("")) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        requestHandlers.execute(() -> {
            try {
                Response response = handleMethod(key, request.getMethod(), request.getBody());
                session.sendResponse(response);
            } catch (IOException e) {
                try {
                    session.sendResponse(new Response(Response.INTERNAL_ERROR, Response.EMPTY));
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    Response handleMethod(String key, int method, byte[] body) throws IOException {
        switch (method) {
            case Request.METHOD_GET:
                return handleGet(key);
            case Request.METHOD_DELETE:
                return handleDelete(key);
            case Request.METHOD_PUT:
                return handlePut(key, body);
            default:
                return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
        }
    }

    private Response handlePut(String keyString, byte[] body) {
        MemorySegment key = MemorySegment.ofArray(keyString.getBytes(StandardCharsets.UTF_8));
        MemorySegment value = MemorySegment.ofArray(body);
        Entry entry = new BaseEntry(key, value);
        dao.upsert(entry);
        return new Response(
                String.valueOf(HttpURLConnection.HTTP_CREATED),
                Response.EMPTY
        );
    }

    private Response handleDelete(String keyString) {
        MemorySegment key = MemorySegment.ofArray(keyString.getBytes(StandardCharsets.UTF_8));
        Entry entry = new BaseEntry(key, null);
        dao.upsert(entry);
        return new Response(
                String.valueOf(HttpURLConnection.HTTP_ACCEPTED),
                Response.EMPTY
        );
    }

    private Response handleGet(String keyString) throws IOException {
        MemorySegment key = MemorySegment.ofArray(keyString.getBytes(StandardCharsets.UTF_8));
        Entry<MemorySegment> value = dao.get(key);
        if (value != null) {
            return new Response(
                    String.valueOf(HttpURLConnection.HTTP_OK),
                    value.value().asByteBuffer().array()
            );
        }

        return new Response(
                String.valueOf(HttpURLConnection.HTTP_NOT_FOUND),
                Response.EMPTY
        );
    }

    @Override
    public synchronized void start() {
        Config daoConfig = new Config(workDir, flushDaoThresholdBytes);
        requestHandlers = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        try {
            dao = new MemorySegmentDao(daoConfig);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        super.start();
    }

    @Override
    public synchronized void stop() {
        requestHandlers.shutdown();
        super.stop();

        for (SelectorThread thread : selectors) {
            for (Session session : thread.selector) {
                session.close();
            }
        }
        try {
            dao.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
