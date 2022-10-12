package ok.dht.test.trofimov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.trofimov.HttpClient.MyHttpClient;
import ok.dht.test.trofimov.dao.BaseEntry;
import ok.dht.test.trofimov.dao.Config;
import ok.dht.test.trofimov.dao.Entry;
import ok.dht.test.trofimov.dao.MyHttpServer;
import ok.dht.test.trofimov.dao.impl.InMemoryDao;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import static java.net.HttpURLConnection.HTTP_ACCEPTED;
import static java.net.HttpURLConnection.HTTP_BAD_GATEWAY;
import static java.net.HttpURLConnection.HTTP_BAD_METHOD;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_GATEWAY_TIMEOUT;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_MOVED_PERM;
import static java.net.HttpURLConnection.HTTP_MULT_CHOICE;
import static java.net.HttpURLConnection.HTTP_NOT_ACCEPTABLE;
import static java.net.HttpURLConnection.HTTP_NOT_AUTHORITATIVE;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NOT_IMPLEMENTED;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_PARTIAL;
import static java.net.HttpURLConnection.HTTP_RESET;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;
import static java.net.HttpURLConnection.HTTP_VERSION;

public class MyService implements Service {

    public static final String PATH_V0_ENTITY = "/v0/entity";
    private static final Logger logger = LoggerFactory.getLogger(MyService.class);
    private static final long FLUSH_THRESHOLD = 1 << 20;
    private static final int REQUESTS_MAX_QUEUE_SIZE = 1024;
    private final ServiceConfig config;
    private HttpServer server;
    private InMemoryDao dao;
    private ThreadPoolExecutor requestsExecutor;
    private MyHttpClient client = new MyHttpClient();

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
                    HttpResponse<byte[]> httpResponse = client.get(node, id);
                    response = new Response(getResponseStatusCode(httpResponse.statusCode()), httpResponse.body());
                }
            } catch (Exception e) {
                logger.error("Error while process request with key " + id, e);
                response = errorResponse(e);
            }
            sendResponse(session, response);
        });
    }

    public static String getResponseStatusCode(int statusCode) {
        return statusCode + switch (statusCode) {
            case HTTP_OK -> " OK";
            case HTTP_CREATED -> " Created";
            case HTTP_ACCEPTED -> " Accepted";
            case HTTP_NOT_AUTHORITATIVE -> " Non-Authoritative Information";
            case HTTP_NO_CONTENT -> " No Content";
            case HTTP_RESET -> " Reset Content";
            case HTTP_PARTIAL -> " Partial Content";
            case HTTP_MULT_CHOICE -> " Multiple Choices";
            case HTTP_MOVED_PERM -> " Moved Permanently";
            case HTTP_BAD_REQUEST -> " Bad Request";
            case HTTP_UNAUTHORIZED -> " Unauthorized";
            case HTTP_FORBIDDEN -> " Forbidden";
            case HTTP_NOT_FOUND -> " Not Found";
            case HTTP_BAD_METHOD -> " Method Not Allowed";
            case HTTP_NOT_ACCEPTABLE -> " Not Acceptable";
            case HTTP_CLIENT_TIMEOUT -> " Request Time-Out";
            case HTTP_INTERNAL_ERROR -> " Internal Server Error";
            case HTTP_NOT_IMPLEMENTED -> " Not Implemented";
            case HTTP_BAD_GATEWAY -> " Bad Gateway";
            case HTTP_UNAVAILABLE -> " Service Unavailable";
            case HTTP_GATEWAY_TIMEOUT -> " Gateway Timeout";
            case HTTP_VERSION -> " HTTP Version Not Supported";
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
            int hash = url.hashCode() + key.hashCode();
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
                    HttpResponse<byte[]> httpResponse = client.upsert(node, id, request.getBody());
                    response = new Response(getResponseStatusCode(httpResponse.statusCode()), httpResponse.body());
                }
            } catch (Exception e) {
                logger.error("Error while process request with key " + id, e);
                response = errorResponse(e);
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
                    HttpResponse<byte[]> httpResponse = client.delete(node, id);
                    response = new Response(getResponseStatusCode(httpResponse.statusCode()), httpResponse.body());
                }
            } catch (Exception e) {
                logger.error("Error while process request with key " + id, e);
                response = errorResponse(e);
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

    @ServiceFactory(stage = 3, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new MyService(config);
        }
    }
}

