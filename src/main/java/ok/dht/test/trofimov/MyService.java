package ok.dht.test.trofimov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.trofimov.dao.BaseEntry;
import ok.dht.test.trofimov.dao.Config;
import ok.dht.test.trofimov.dao.Entry;
import ok.dht.test.trofimov.dao.MyHttpServer;
import ok.dht.test.trofimov.dao.impl.InMemoryDao;
import ok.dht.test.trofimov.httpclient.MyHttpClient;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import one.nio.util.Base64;
import one.nio.util.Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MyService implements Service {

    public static final String PATH_V0_ENTITY = "/v0/entity";
    private static final Logger logger = LoggerFactory.getLogger(MyService.class);
    private static final long FLUSH_THRESHOLD = 1 << 20;
    private static final int REQUESTS_MAX_QUEUE_SIZE = 256;
    private final ServiceConfig config;
    private HttpServer server;
    private InMemoryDao dao;
    private ThreadPoolExecutor requestsExecutor;
    private Map<Long, MyHttpClient> clients;

    public MyService(ServiceConfig config) {
        this.config = config;
    }

    private Config createDaoConfig() {
        return new Config(config.workingDir(), FLUSH_THRESHOLD);
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        dao = new InMemoryDao(createDaoConfig());
        initExecutor();
        server = new MyHttpServer(createConfigFromPort(config.selfPort()));
        server.start();
        server.addRequestHandlers(this);
        return CompletableFuture.completedFuture(null);
    }

    private void initExecutor() {
        int threadsCount = Runtime.getRuntime().availableProcessors();
        requestsExecutor = new ThreadPoolExecutor(threadsCount, threadsCount,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(REQUESTS_MAX_QUEUE_SIZE),
                new ThreadPoolExecutor.DiscardOldestPolicy());
        requestsExecutor.prestartAllCoreThreads();

        clients = new HashMap<>();
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        requestsExecutor.shutdownNow();
        dao.close();
        return CompletableFuture.completedFuture(null);
    }

    @Path(PATH_V0_ENTITY)
    @RequestMethod(Request.METHOD_GET)
    public void handleGet(@Param(value = "id", required = true) String id, HttpSession session) {
        requestsExecutor.execute(() -> {
            Response response;
            try {
                String node = getNodeOf(config.clusterUrls(), id);
                if (node.isEmpty() || node.equals(config.selfUrl())) {
                    if (id.isEmpty()) {
                        response = emptyResponseFor(Response.BAD_REQUEST);
                    } else {
                        Entry<String> entry = dao.get(id);
                        if (entry == null) {
                            response = emptyResponseFor(Response.NOT_FOUND);
                        } else {
                            String value = entry.value();
                            char[] chars = value.toCharArray();
                            response = new Response(Response.OK, Base64.decodeFromChars(chars));
                        }
                    }
                } else {
                    MyHttpClient httpClient = clients.computeIfAbsent(Thread.currentThread().getId(),
                            k -> new MyHttpClient());
                    HttpResponse<byte[]> httpResponse = httpClient.get(node, id);
                    response = new Response(getResponseStatusCode(httpResponse.statusCode()), httpResponse.body());
                }
            } catch (Exception e) {
                logger.error("Error while process request with key " + id, e);
                response = errorResponse();
            }
            sendResponse(session, response);
        });
    }

    public static String getResponseStatusCode(int statusCode) {
        return switch (statusCode) {
            case HttpURLConnection.HTTP_OK -> "200 OK";
            case HttpURLConnection.HTTP_CREATED -> "201 Created";
            case HttpURLConnection.HTTP_ACCEPTED -> "202 Accepted";
            case HttpURLConnection.HTTP_NOT_AUTHORITATIVE -> "203 Non-Authoritative Information";
            case HttpURLConnection.HTTP_NO_CONTENT -> "204 No Content";
            case HttpURLConnection.HTTP_RESET -> "205 Reset Content";
            case HttpURLConnection.HTTP_PARTIAL -> "206 Partial Content";
            case HttpURLConnection.HTTP_MULT_CHOICE -> "300 Multiple Choices";
            case HttpURLConnection.HTTP_MOVED_PERM -> "301 Moved Permanently";
            case HttpURLConnection.HTTP_BAD_REQUEST -> "400 Bad Request";
            case HttpURLConnection.HTTP_UNAUTHORIZED -> "401 Unauthorized";
            case HttpURLConnection.HTTP_FORBIDDEN -> "403 Forbidden";
            case HttpURLConnection.HTTP_NOT_FOUND -> "404 Not Found";
            case HttpURLConnection.HTTP_BAD_METHOD -> "405 Method Not Allowed";
            case HttpURLConnection.HTTP_NOT_ACCEPTABLE -> "406 Not Acceptable";
            case HttpURLConnection.HTTP_CLIENT_TIMEOUT -> "408 Request Time-Out";
            case HttpURLConnection.HTTP_INTERNAL_ERROR -> "500 Internal Server Error";
            case HttpURLConnection.HTTP_NOT_IMPLEMENTED -> "501 Not Implemented";
            case HttpURLConnection.HTTP_BAD_GATEWAY -> "502 Bad Gateway";
            case HttpURLConnection.HTTP_UNAVAILABLE -> "503 Service Unavailable";
            case HttpURLConnection.HTTP_GATEWAY_TIMEOUT -> "504 Gateway Timeout";
            case HttpURLConnection.HTTP_VERSION -> "505 HTTP Version Not Supported";
            default -> " ";
        };
    }

    private static String getNodeOf(List<String> clusterUrls, String key) {
        if (clusterUrls.isEmpty()) {
            return "";
        }
        int maxHash = Integer.MIN_VALUE;
        String node = "";
        for (String url : clusterUrls) {
            int hash = Hash.murmur3(url) + Hash.murmur3(key);
            if (hash > maxHash) {
                maxHash = hash;
                node = url;
            }
        }
        return node;
    }

    @Path(PATH_V0_ENTITY)
    @RequestMethod(Request.METHOD_PUT)
    public void handlePut(Request request, @Param(value = "id", required = true) String id, HttpSession session) {
        requestsExecutor.execute(() -> {
            Response response;
            try {
                String node = getNodeOf(config.clusterUrls(), id);
                if (node.isEmpty() || node.equals(config.selfUrl())) {

                    if (id.isEmpty()) {
                        response = emptyResponseFor(Response.BAD_REQUEST);
                    } else {
                        byte[] value = request.getBody();
                        dao.upsert(new BaseEntry<>(id, new String(Base64.encodeToChars(value))));
                        response = emptyResponseFor(Response.CREATED);
                    }
                } else {
                    MyHttpClient httpClient = clients.computeIfAbsent(Thread.currentThread().getId(),
                            k -> new MyHttpClient());
                    HttpResponse<byte[]> httpResponse = httpClient.upsert(node, id, request.getBody());
                    response = new Response(getResponseStatusCode(httpResponse.statusCode()), httpResponse.body());
                }
            } catch (Exception e) {
                logger.error("Error while process request with key " + id, e);
                response = errorResponse();
            }
            sendResponse(session, response);
        });
    }

    @Path(PATH_V0_ENTITY)
    @RequestMethod(Request.METHOD_DELETE)
    public void handleDelete(@Param(value = "id", required = true) String id, HttpSession session) {
        requestsExecutor.execute(() -> {
            Response response;
            try {
                String node = getNodeOf(config.clusterUrls(), id);
                if (node.isEmpty() || node.equals(config.selfUrl())) {
                    if (id.isEmpty()) {
                        response = emptyResponseFor(Response.BAD_REQUEST);
                    } else {
                        dao.upsert(new BaseEntry<>(id, null));
                        response = emptyResponseFor(Response.ACCEPTED);
                    }
                } else {
                    MyHttpClient httpClient = clients.computeIfAbsent(Thread.currentThread().getId(),
                            k -> new MyHttpClient());
                    HttpResponse<byte[]> httpResponse = httpClient.delete(node, id);
                    response = new Response(getResponseStatusCode(httpResponse.statusCode()), httpResponse.body());
                }
            } catch (Exception e) {
                logger.error("Error while process request with key " + id, e);
                response = errorResponse();
            }
            sendResponse(session, response);
        });

    }

    private static void sendResponse(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e) {
            logger.error("Error send response", e);
            closeSession(session);
        }
    }

    private static void closeSession(HttpSession session) {
        try {
            session.close();
        } catch (Exception e) {
            logger.error("Error in closing session", e);
        }
    }

    @Path(PATH_V0_ENTITY)
    @RequestMethod(Request.METHOD_POST)
    public Response handlePost() {
        return emptyResponseFor(Response.METHOD_NOT_ALLOWED);
    }

    private Response emptyResponseFor(String status) {
        return new Response(status, Response.EMPTY);
    }

    private Response errorResponse() {
        return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @ServiceFactory(stage = 3, week = 2, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new MyService(config);
        }
    }
}

