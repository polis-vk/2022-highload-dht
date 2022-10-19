package ok.dht.test.pobedonostsev;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.pobedonostsev.dao.BaseEntry;
import ok.dht.test.pobedonostsev.dao.Config;
import ok.dht.test.pobedonostsev.dao.Dao;
import ok.dht.test.pobedonostsev.dao.Entry;
import ok.dht.test.pobedonostsev.dao.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import one.nio.util.Hash;
import one.nio.util.Utf8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CustomHttpServer extends HttpServer {
    private static final Logger LOG = LoggerFactory.getLogger(CustomHttpServer.class);
    private static final String PATH = "/v0/entity";
    private static final long FLUSH_THRESHOLD_BYTES = 1 << 20;
    private static final int THREAD_COUNT = 4;
    private static final int QUEUE_SIZE = 256;
    private final HttpClient client;
    private final Dao<MemorySegment, Entry<MemorySegment>> dao;
    private final ServiceConfig config;
    private boolean closed;
    private ExecutorService selfExecutor;
    private ExecutorService proxyExecutor;

    public CustomHttpServer(ServiceConfig config) throws IOException {
        super(createConfigFromPort(config.selfPort()));
        dao = new MemorySegmentDao(new Config(config.workingDir(), FLUSH_THRESHOLD_BYTES));
        this.config = config;
        client = HttpClient.newHttpClient();
    }

    private static void sendError(HttpSession session, Exception e) {
        try {
            session.sendResponse(new Response(Response.INTERNAL_ERROR, Response.EMPTY));
            LOG.error("Cannot handle", e);
        } catch (IOException ex) {
            LOG.error("Cannot send response", e);
        }
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[] {acceptor};
        return httpConfig;
    }

    private static String getNode(List<String> urls, String key) {
        int maxHash = Integer.MIN_VALUE;
        String nodeUrl = null;
        for (String url : urls) {
            int hash = Hash.murmur3(url) + Hash.murmur3(key);
            if (hash >= maxHash) {
                maxHash = hash;
                nodeUrl = url;
            }
        }
        return nodeUrl;
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        try {
            if (!PATH.equals(request.getPath())) {
                session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                return;
            }
            String key = request.getParameter("id=");
            if (key == null || key.isEmpty()) {
                session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                return;
            }
            String url = getNode(config.clusterUrls(), key);

            if (url.equals(config.selfUrl())) {
                selfExecutor.execute(() -> {
                    try {
                        session.sendResponse(handle(request, key));
                    } catch (Exception e) {
                        sendError(session, e);
                    }
                });
            } else {
                selfExecutor.execute(() -> {
                    try {
                        session.sendResponse(proxyRequest(request, url));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        sendError(session, e);
                    } catch (Exception e) {
                        sendError(session, e);
                    }
                });
            }
        } catch (Exception e) {
            sendError(session, e);
        }
    }

    private Response proxyRequest(Request request, String url) throws IOException, InterruptedException {
        URI uriFromRequest = URI.create(request.getURI());
        byte[] body = request.getBody() == null ? new byte[] {} : request.getBody();
        HttpRequest newRequest =
                HttpRequest.newBuilder(URI.create(url + uriFromRequest.getPath() + '?' + uriFromRequest.getQuery()))
                        .method(request.getMethodName(), HttpRequest.BodyPublishers.ofByteArray(body)).build();
        HttpResponse<byte[]> response = client.send(newRequest, HttpResponse.BodyHandlers.ofByteArray());
        String status = switch (response.statusCode()) {
            case HttpURLConnection.HTTP_OK -> Response.OK;
            case HttpURLConnection.HTTP_CREATED -> Response.CREATED;
            case HttpURLConnection.HTTP_ACCEPTED -> Response.ACCEPTED;
            case HttpURLConnection.HTTP_NO_CONTENT -> Response.NO_CONTENT;
            case HttpURLConnection.HTTP_SEE_OTHER -> Response.SEE_OTHER;
            case HttpURLConnection.HTTP_NOT_MODIFIED -> Response.NOT_MODIFIED;
            case HttpURLConnection.HTTP_USE_PROXY -> Response.USE_PROXY;
            case HttpURLConnection.HTTP_BAD_REQUEST -> Response.BAD_REQUEST;
            case HttpURLConnection.HTTP_UNAUTHORIZED -> Response.UNAUTHORIZED;
            case HttpURLConnection.HTTP_PAYMENT_REQUIRED -> Response.PAYMENT_REQUIRED;
            case HttpURLConnection.HTTP_FORBIDDEN -> Response.FORBIDDEN;
            case HttpURLConnection.HTTP_NOT_FOUND -> Response.NOT_FOUND;
            case HttpURLConnection.HTTP_NOT_ACCEPTABLE -> Response.NOT_ACCEPTABLE;
            case HttpURLConnection.HTTP_CONFLICT -> Response.CONFLICT;
            case HttpURLConnection.HTTP_GONE -> Response.GONE;
            case HttpURLConnection.HTTP_BAD_METHOD -> Response.METHOD_NOT_ALLOWED;
            case HttpURLConnection.HTTP_LENGTH_REQUIRED -> Response.LENGTH_REQUIRED;
            case HttpURLConnection.HTTP_NOT_IMPLEMENTED -> Response.NOT_IMPLEMENTED;
            case HttpURLConnection.HTTP_BAD_GATEWAY -> Response.BAD_GATEWAY;
            case HttpURLConnection.HTTP_GATEWAY_TIMEOUT -> Response.GATEWAY_TIMEOUT;
            default -> throw new IllegalArgumentException("Bad status: " + response.statusCode());
        };
        return new Response(status, response.body());
    }

    private Response handle(Request request, String key) throws IOException {
        return switch (request.getMethod()) {
            case Request.METHOD_GET -> {
                Entry<MemorySegment> entry = dao.get(MemorySegment.ofArray(Utf8.toBytes(key)));
                if (entry == null) {
                    yield new Response(Response.NOT_FOUND, Response.EMPTY);
                }
                yield new Response(Response.OK, entry.value().toByteArray());
            }
            case Request.METHOD_PUT -> {
                MemorySegment value = MemorySegment.ofArray(request.getBody());
                dao.upsert(new BaseEntry<>(MemorySegment.ofArray(Utf8.toBytes(key)), value));
                yield new Response(Response.CREATED, Response.EMPTY);
            }
            case Request.METHOD_DELETE -> {
                dao.upsert(new BaseEntry<>(MemorySegment.ofArray(Utf8.toBytes(key)), null));
                yield new Response(Response.ACCEPTED, Response.EMPTY);
            }
            default -> new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
        };
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
        session.sendResponse(response);
    }

    @Override
    public synchronized void start() {
        selfExecutor = new ThreadPoolExecutor(THREAD_COUNT, THREAD_COUNT, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_SIZE));
        proxyExecutor = new ThreadPoolExecutor(THREAD_COUNT, THREAD_COUNT, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_SIZE));
        super.start();
        closed = false;
    }

    @Override
    public synchronized void stop() {
        if (closed) {
            return;
        }
        for (SelectorThread selector : selectors) {
            for (Session session : selector.selector) {
                session.close();
            }
        }
        closed = true;
        super.stop();
        try {
            shutdownExecutor(selfExecutor);
            shutdownExecutor(proxyExecutor);
        } catch (InterruptedException e) {
            LOG.error("Interrupted while terminating", e);
            Thread.currentThread().interrupt();
        }
        try {
            dao.close();
        } catch (IOException e) {
            LOG.error("Cannot close dao");
            throw new RuntimeException(e);
        }
    }

    private void shutdownExecutor(ExecutorService executor) throws InterruptedException {
        selfExecutor.shutdown();
        boolean terminated = selfExecutor.awaitTermination(5, TimeUnit.SECONDS);
        if (!terminated) {
            LOG.error("Termination timeout");
        }
    }
}
