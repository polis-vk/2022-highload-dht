package ok.dht.test.garanin;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.garanin.db.Db;
import ok.dht.test.garanin.db.DbException;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import org.rocksdb.RocksDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

public class DhtService implements Service {

    private static final Logger LOGGER = LoggerFactory.getLogger(DhtServer.class);

    private final ServiceConfig config;
    private final ExecutorService executorService = new ForkJoinPool();
    private final HttpClient httpClient;
    private HttpServer server;
    private RocksDB rocksDB;

    public DhtService(ServiceConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(500))
                .build();
    }

    private static int hash(String str) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(str.getBytes(Charset.defaultCharset()));
            return Math.abs(ByteBuffer.wrap(bytes).getInt());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private String getUrl(String id) {
        int hash = hash(id);
        String url = config.clusterUrls().get(hash % config.clusterUrls().size());
        if (url.equals(config.selfUrl())) {
            return null;
        }
        return url;
    }

    private static boolean validateId(String id) {
        return !id.isEmpty();
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        Collections.sort(config.clusterUrls());
        rocksDB = Db.open(config.workingDir());
        server = new DhtHttpServer(createConfigFromPort(config.selfPort()));
        server.start();
        server.addRequestHandlers(this);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        if (rocksDB != null) {
            Db.close(rocksDB);
        }
        rocksDB = null;
        server.stop();
        return CompletableFuture.completedFuture(null);
    }

    public Response handleGet(String id) {
        byte[] value;
        try {
            value = Db.get(rocksDB, id);
        } catch (DbException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
        if (value == null) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }
        return new Response(Response.OK, value);
    }

    public Response handlePut(Request request, String id) {
        try {
            Db.put(rocksDB, id, request.getBody());
        } catch (DbException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
        return new Response(Response.CREATED, Response.EMPTY);
    }

    public Response handleDelete(String id) {
        try {
            Db.delete(rocksDB, id);
        } catch (DbException e) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    private class DhtHttpServer extends HttpServer {

        private static final Set<Integer> ALLOWED_METHODS = Set.of(
                Request.METHOD_GET,
                Request.METHOD_PUT,
                Request.METHOD_DELETE
        );

        public DhtHttpServer(HttpServerConfig config, Object... routers) throws IOException {
            super(config, routers);
        }

        @Override
        public void handleRequest(Request request, HttpSession session) throws IOException {
            if (!request.getPath().equals("/v0/entity")) {
                session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                return;
            }
            if (!ALLOWED_METHODS.contains(request.getMethod())) {
                session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
                return;
            }
            String id = request.getParameter("id=");
            if (id == null || !validateId(id)) {
                session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                return;
            }
            executorService.execute(() -> {
                String url = getUrl(id);
                if (url == null) {
                    Response response = switch (request.getMethod()) {
                        case Request.METHOD_GET -> handleGet(id);
                        case Request.METHOD_PUT -> handlePut(request, id);
                        case Request.METHOD_DELETE -> handleDelete(id);
                        default -> throw new IllegalStateException();
                    };
                    sendResponse(session, response);
                    return;
                }
                HttpRequest proxyRequest = HttpRequest.newBuilder()
                        .uri(URI.create(url + request.getURI()))
                        .method(request.getMethodName(), request.getBody() == null
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofByteArray(request.getBody()))
                        .build();
                httpClient.sendAsync(proxyRequest, HttpResponse.BodyHandlers.ofByteArray())
                        .exceptionally(ex -> null)
                        .thenAcceptAsync((response) -> {
                            if (response != null) {
                                sendResponse(session,
                                        new Response(convertStatus(response.statusCode()), response.body())
                                );
                            } else {
                                sendResponse(session, new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY));
                            }
                        });
            });
        }

        @Override
        public synchronized void stop() {
            for (var selectorThread : selectors) {
                if (selectorThread.selector.isOpen()) {
                    for (var session : selectorThread.selector) {
                        session.close();
                    }
                }
            }
            super.stop();
        }
    }

    private static void sendResponse(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
            session.close();
        }
    }

    private static String convertStatus(int statusCode) {
        return switch (statusCode) {
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
            case HttpURLConnection.HTTP_LENGTH_REQUIRED -> Response.LENGTH_REQUIRED;
            case HttpURLConnection.HTTP_INTERNAL_ERROR -> Response.INTERNAL_ERROR;
            case HttpURLConnection.HTTP_NOT_IMPLEMENTED -> Response.NOT_IMPLEMENTED;
            case HttpURLConnection.HTTP_BAD_GATEWAY -> Response.BAD_GATEWAY;
            case HttpURLConnection.HTTP_GATEWAY_TIMEOUT -> Response.GATEWAY_TIMEOUT;
            default -> throw new IllegalArgumentException("Status code " + statusCode + " not implemented");
        };
    }

    @ServiceFactory(stage = 3, week = 2, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new DhtService(config);
        }
    }
}
