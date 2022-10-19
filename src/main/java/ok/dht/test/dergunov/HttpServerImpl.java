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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static ok.dht.test.dergunov.ConverterStatusCode.fromHttpResponseStatusJavaToOneNoi;
import static one.nio.http.Request.METHOD_DELETE;
import static one.nio.http.Request.METHOD_GET;
import static one.nio.http.Request.METHOD_PUT;

public class HttpServerImpl extends HttpServer {

    private static final Log LOGGER = LogFactory.getLog(HttpServerImpl.class);
    private static final String PATH = "/v0/entity";
    private static final String PARAMETER_KEY = "id=";

    private static final int SIZE_QUEUE = 128;
    private static final int COUNT_CORES = 4;

    private static final Set<Integer> SUPPORTED_METHODS = Set.of(METHOD_GET, METHOD_PUT, METHOD_DELETE);
    private static final Response BAD_RESPONSE = new Response(Response.BAD_REQUEST, Response.EMPTY);
    private static final Response METHOD_NOT_ALLOWED = new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
    private static final Response SERVICE_UNAVAILABLE = new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
    private final MemorySegmentDao database;
    private final PathMapper handlerMapper = new PathMapper();

    private final ExecutorService poolExecutor = new ThreadPoolExecutor(
            COUNT_CORES,
            COUNT_CORES,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(SIZE_QUEUE)
    );

    private final HttpClient httpClient;
    private final String selfUrl;
    private final Set<String> illNodes = new HashSet<>();
    private final ShardMapper shardMapper;

    public HttpServerImpl(HttpServerConfig httpServerConfig, MemorySegmentDao database, String selfUrl,
                          ShardMapper shardMapper,
                          Object... routers) throws IOException {
        super(httpServerConfig, routers);
        this.database = database;
        this.selfUrl = selfUrl;
        this.shardMapper = shardMapper;
        handlerMapper.add(PATH, new int[]{METHOD_GET}, this::handleGet);
        handlerMapper.add(PATH, new int[]{METHOD_PUT}, this::handlePut);
        handlerMapper.add(PATH, new int[]{METHOD_DELETE}, this::handleDelete);
        httpClient = HttpClient.newHttpClient();
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

                String key = getEntityId(request, session);
                if (key == null) return;

                String url = shardMapper.getShardByKey(key);
                LOGGER.info("url:" + url);

                if (url.equals(selfUrl)) {
                    LOGGER.info("selfurl:" + selfUrl);
                    RequestHandler handler = handlerMapper.find(path, methodName);
                    if (handler != null) {
                        handler.handleRequest(request, session);
                        return;
                    }
                    handleDefault(request, session);
                    return;
                }

                if (illNodes.contains(url)) {
                    LOGGER.error("Error when send response (node ill) url : " + url);
                    handleUnavailable(session);
                    return;
                }

                proxy(request, session, url);
            } catch (IOException e) {
                handleUnavailable(session);
            }
        });
    }

    private void proxy(Request request, HttpSession session, String url) throws IOException {
        HttpRequest proxyRequest = HttpRequest.newBuilder(URI.create(url + request.getURI()))
                        .method(request.getMethodName(),
                                HttpRequest.BodyPublishers.ofByteArray(request.getBody()))
                .build();

        try {
            HttpResponse<byte[]> httpResponse = httpClient
                    .send(proxyRequest, HttpResponse.BodyHandlers.ofByteArray());
            session.sendResponse(new Response(fromHttpResponseStatusJavaToOneNoi(httpResponse.statusCode()),
                            httpResponse.body()));
        } catch (Exception e) {
            illNodes.add(url);
            LOGGER.error("Error when send response (node ill) url : " + url);
            handleUnavailable(session);
        }
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
        String entityId = getEntityId(request, session);
        if (entityId == null) return;

        Entry<MemorySegment> result = database.get(fromString(entityId));
        if (result == null) {
            session.sendResponse(new Response(Response.NOT_FOUND, Response.EMPTY));
            return;
        }
        session.sendResponse(new Response(Response.OK, toBytes(result.value())));
    }

    private void handlePut(@Nonnull Request request, HttpSession session) throws IOException {
        String entityId = getEntityId(request, session);
        if (entityId == null) return;

        database.upsert(new BaseEntry<>(fromString(entityId), fromBytes(request.getBody())));
        session.sendResponse(new Response(Response.CREATED, Response.EMPTY));
    }

    private void handleDelete(@Nonnull Request request, HttpSession session) throws IOException {
        String entityId = getEntityId(request, session);
        if (entityId == null) return;

        database.upsert(new BaseEntry<>(fromString(entityId), null));
        session.sendResponse(new Response(Response.ACCEPTED, Response.EMPTY));
    }

    private static String getEntityId(Request request, HttpSession session) throws IOException {
        String entityId = request.getParameter(PARAMETER_KEY, "");
        if (entityId.isEmpty()) {
            session.sendResponse(BAD_RESPONSE);
            return null;
        }
        return entityId;
    }

}
