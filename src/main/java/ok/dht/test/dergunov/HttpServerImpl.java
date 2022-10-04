package ok.dht.test.dergunov;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.dergunov.database.BaseEntry;
import ok.dht.test.dergunov.database.Config;
import ok.dht.test.dergunov.database.Entry;
import ok.dht.test.dergunov.database.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.PathMapper;
import one.nio.http.Request;
import one.nio.http.RequestHandler;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import one.nio.util.Utf8;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class HttpServerImpl extends HttpServer {

    private final ConcurrentLinkedDeque<Request> requestsQueue = new ConcurrentLinkedDeque<>();
    private final MemorySegmentDao database;
    private final PathMapper defaultMapper = new PathMapper();

    private final ExecutorService poolExecutor = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingDeque<>()
    );


    public HttpServerImpl(HttpServerConfig config, ServiceConfig configs, long flushThresholdBytes, Object... routers) throws IOException {
        super(config, routers);
        database = new MemorySegmentDao(new Config(configs.workingDir(), flushThresholdBytes));
        defaultMapper.add("/v0/entity", new int[]{Request.METHOD_GET}, this::handleGet);
        defaultMapper.add("/v0/entity", new int[]{Request.METHOD_PUT}, this::handlePut);
        defaultMapper.add("/v0/entity", new int[]{Request.METHOD_DELETE}, this::handleDelete);
    }

    public static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    private static String toString(MemorySegment data) {
        return data == null ? null : new String(data.toByteArray());
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
        Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
        session.sendResponse(response);
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        //super.handleRequest(request, session);
        poolExecutor.submit(() -> {
            int methodName = request.getMethod();
            String path = request.getPath();

            RequestHandler handler = defaultMapper.find(path, methodName);
            if (handler != null) {
                try {
                    handler.handleRequest(request, session);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return;
            }

            try {
                handleDefault(request, session);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
//            if (request == null || !request.getPath().equals("/v0/entity")) {
//                return new Response(Response.BAD_REQUEST, Response.EMPTY);
//            }
//            System.out.println("here switch");
//            return switch (request.getMethodName()) {
//                case "GET" -> handleGet(request, database);
//                case "PUT"  -> handlePut(request, database);
//                case "DELETE" -> handleDelete(request, database);
//                default -> new Response(Response.BAD_REQUEST, Response.EMPTY);
//            };
        });
    }

    private void handleGet(@Nonnull Request request, HttpSession session) throws IOException {
        System.out.println("here get");
        String entityId = request.getParameter("id=", "");
        System.out.println("entityId:" + entityId);

        if (entityId.isEmpty()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        Entry<MemorySegment> result = database.get(fromString(entityId));
        if (result == null) {

            session.sendResponse(new Response(Response.NOT_FOUND, Response.EMPTY));
            return;
        }
        System.out.println("key: " + toString(result.key()));
        System.out.println("value: " + toString(result.value()));
        session.sendResponse(new Response(Response.OK, toBytes(result.value())));
    }

    private void handlePut(@Nonnull Request request, HttpSession session) throws IOException {
        System.out.println("here put");

        String entityId = request.getParameter("id", "");
        System.out.println("entityId:" + entityId);
        if (entityId.isEmpty()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        database.upsert(new BaseEntry<>(fromString(entityId), fromBytes(request.getBody())));
        session.sendResponse(new Response(Response.CREATED, Response.EMPTY));
    }

    private void handleDelete(@Nonnull Request request, HttpSession session) throws IOException {
        System.out.println("here delete");

        String entityId = request.getParameter("id", "");
        System.out.println("entityId:" + entityId);

        if (entityId.isEmpty()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        database.upsert(new BaseEntry<>(fromString(entityId), null));
        session.sendResponse(new Response(Response.ACCEPTED, Response.EMPTY));
    }

}
