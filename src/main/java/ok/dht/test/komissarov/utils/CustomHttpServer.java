package ok.dht.test.komissarov.utils;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.komissarov.database.MemorySegmentDao;
import ok.dht.test.komissarov.database.models.BaseEntry;
import ok.dht.test.komissarov.database.models.Config;
import ok.dht.test.komissarov.database.models.Entry;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CustomHttpServer extends HttpServer {

    private static final String PATH = "/v0/entity";
    private static final String PARAM_KEY = "id=";

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomHttpServer.class);

    private final ExecutorService nodeWorkers =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() / 2);
    private final ExecutorService binder = Executors.newSingleThreadExecutor();
    private final ServiceConfig config;
    private final HttpClient client;
    private final MemorySegmentDao dao;

    public CustomHttpServer(ServiceConfig config) throws IOException {
        super(createConfigFromPort(config.selfPort()));

        this.config = config;
        client = HttpClient.newHttpClient();
        dao = new MemorySegmentDao(new Config(
                config.workingDir(),
                1 << 20
        ));
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        String path = request.getPath();
        if (!path.equals(PATH)) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        String id = request.getParameter(PARAM_KEY);
        if (id == null || id.isEmpty()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }
        handle(request, session , id);
    }

    @Override
    public synchronized void stop() {
        try {
            nodeWorkers.shutdown();
            binder.shutdown();
            super.stop();
            for (SelectorThread thread : selectors) {
                thread.selector.forEach(Session::close);
            }
            dao.close();
        } catch (IOException e) {
            LOGGER.error("Stop error", e);
        }
    }

    private static MemorySegment fromString(String value) {
        return value == null ? null : MemorySegment.ofArray(Utf8.toBytes(value));
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    private void handle(Request request, HttpSession session, String id) {
        String url = getNode(id);
        if (config.selfUrl().equals(url)) {
            nodeWorkers.execute(() -> {
                try {
                    Response response = getResponse(request, id);
                    send(session, response);
                } catch (Exception e) {
                    LOGGER.error("Unavailable error", e);
                    send(session, new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY));
                }
            });
        } else {
            binder.execute(() -> {
                Response response = getProxyResponse(request, url);
                send(session, response);
            });
        }
    }

    private void send(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e) {
            LOGGER.error("Send response error", e);
            session.close();
        }
    }

    private Response getProxyResponse(Request request, String url) {
        try {
            HttpRequest proxyRequest = HttpRequest.newBuilder()
                    .uri(new URI(url + request.getURI()))
                    .method(request.getMethodName(), HttpRequest.BodyPublishers.ofByteArray(request.getBody()))
                    .build();
            HttpResponse<byte[]> response = client.send(proxyRequest, HttpResponse.BodyHandlers.ofByteArray());
            return new Response(mapCode(response.statusCode()), response.body());
        } catch (Exception e) {
            LOGGER.error("Unavailable error", e);
            return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
        }
    }

    private Response getResponse(Request request, String id) {
        int method = request.getMethod();
        switch (method) {
            case Request.METHOD_GET -> {
                Entry<MemorySegment> entry = dao.get(fromString(id));
                if (entry == null) {
                    return new Response(Response.NOT_FOUND, Response.EMPTY);
                }
                return new Response(Response.OK, entry.value().toByteArray());
            }
            case Request.METHOD_PUT -> {
                Entry<MemorySegment> entry = new BaseEntry<>(
                        fromString(id),
                        MemorySegment.ofArray(request.getBody())
                );
                dao.upsert(entry);
                return new Response(Response.CREATED, Response.EMPTY);
            }
            case Request.METHOD_DELETE -> {
                Entry<MemorySegment> removedEntry = new BaseEntry<>(
                        fromString(id),
                        null
                );
                dao.upsert(removedEntry);
                return new Response(Response.ACCEPTED, Response.EMPTY);
            }
            default -> {
                return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            }
        }
    }

    private String getNode(String id) {
        int max = Integer.MIN_VALUE;
        String node = null;

        for (String url : config.clusterUrls()) {
            max = Math.max(max, Hash.murmur3(url + id));
            node = url;
        }
        return node;
    }

    private String mapCode(int code) {
        return switch (code) {
            case HttpURLConnection.HTTP_OK -> Response.OK;
            case HttpURLConnection.HTTP_CREATED -> Response.CREATED;
            case HttpURLConnection.HTTP_ACCEPTED -> Response.ACCEPTED;
            case HttpURLConnection.HTTP_BAD_REQUEST -> Response.BAD_REQUEST;
            case HttpURLConnection.HTTP_NOT_FOUND -> Response.NOT_FOUND;
            default -> throw new IllegalStateException("Unexpected value: " + code);
        };
    }
}
