package ok.dht.test.pashchenko;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.pashchenko.dao.Config;
import ok.dht.test.pashchenko.dao.Entry;
import ok.dht.test.pashchenko.dao.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import one.nio.util.Utf8;

public class MyServer extends HttpServer {
    private static final Logger LOG = LoggerFactory.getLogger(MyServer.class);

    private final MemorySegmentDao dao;
    private final Executor executor;
    private final HttpClient client;
    private final ServiceConfig config;

    public MyServer(ServiceConfig config) throws IOException {
        super(createConfigFromPort(config.selfPort()));
        this.config = config;
        dao = new MemorySegmentDao(new Config(config.workingDir(), 1048576L));
        executor = Executors.newFixedThreadPool(16);
        client = HttpClient.newHttpClient();
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
    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (!"/v0/entity".equals(request.getPath())) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        String id = request.getParameter("id=");
        if (id == null || id.isEmpty()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        executor.execute(() -> {
            try {
                String url = config.clusterUrls().get(getHashForKey(id) % config.clusterUrls().size());
                session.sendResponse(url.equals(config.selfUrl()) ? handleRequest(request, id) : proxyRequest(request, url));
            } catch (Exception e) {
                LOG.error("error handle request", e);
                sendError(session);
            }
        });
    }

    private int getHashForKey(String id) {
        return id.hashCode();
    }

    private static void sendError(HttpSession session) {
        try {
            session.sendResponse(new Response(Response.INTERNAL_ERROR, Response.EMPTY));
        } catch (Exception ex) {
            LOG.error("error send response", ex);
            sessionClose(session);
        }
    }

    private static void sessionClose(HttpSession session) {
        try {
            session.close();
        } catch (Exception e) {
            LOG.error("error close session", e);
        }
    }

    private Response proxyRequest(Request request, String url) throws IOException, InterruptedException {
        HttpRequest proxyRequest = HttpRequest.newBuilder(URI.create(url + request.getURI()))
                .method(
                        request.getMethodName(),
                        HttpRequest.BodyPublishers.ofByteArray(request.getBody())
                ).build();

        HttpResponse<byte[]> response = client.send(proxyRequest, HttpResponse.BodyHandlers.ofByteArray());
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
            case HttpURLConnection.HTTP_LENGTH_REQUIRED -> Response.LENGTH_REQUIRED;
            case HttpURLConnection.HTTP_INTERNAL_ERROR -> Response.INTERNAL_ERROR;
            case HttpURLConnection.HTTP_NOT_IMPLEMENTED -> Response.NOT_IMPLEMENTED;
            case HttpURLConnection.HTTP_BAD_GATEWAY -> Response.BAD_GATEWAY;
            case HttpURLConnection.HTTP_GATEWAY_TIMEOUT -> Response.GATEWAY_TIMEOUT;
            default -> throw new IllegalArgumentException("Unknown status code: " + response.statusCode());
        };
        return new Response(status, response.body());
    }

    private Response handleRequest(Request request, String id) {
        switch (request.getMethod()) {
            case Request.METHOD_GET -> {
                Entry entry = dao.get(MemorySegment.ofArray(Utf8.toBytes(id)));
                if (entry == null) {
                    return new Response(Response.NOT_FOUND, Response.EMPTY);
                }
                return new Response(Response.OK, entry.value().toByteArray());
            }
            case Request.METHOD_PUT -> {
                dao.upsert(new Entry(MemorySegment.ofArray(Utf8.toBytes(id)), MemorySegment.ofArray(request.getBody())));
                return new Response(Response.CREATED, Response.EMPTY);
            }
            case Request.METHOD_DELETE -> {
                dao.upsert(new Entry(MemorySegment.ofArray(Utf8.toBytes(id)), null));
                return new Response(Response.ACCEPTED, Response.EMPTY);
            }
            default -> {
                return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            }
        }
    }

    @Override
    public synchronized void stop() {
        super.stop();
        for (SelectorThread selector : selectors) {
            for (Session session : selector.selector) {
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
