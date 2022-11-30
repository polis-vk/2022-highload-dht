package ok.dht.test.trofimov.dao;

import ok.dht.ServiceConfig;
import ok.dht.test.trofimov.common.Responses;
import ok.dht.test.trofimov.dao.impl.InMemoryDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import one.nio.util.Base64;
import one.nio.util.Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MyHttpServer extends HttpServer {
    private static final Logger logger = LoggerFactory.getLogger(MyHttpServer.class);
    private static final long FLUSH_THRESHOLD = 1 << 20;
    private static final int REQUESTS_MAX_QUEUE_SIZE = 256;
    public static final String TIMESTAMP_HEADER = "X-timestamp: ";
    public static final String X_TOMB_HEADER = "X-tomb: ";
    private final ServiceConfig config;
    private final InMemoryDao dao;
    private ThreadPoolExecutor requestsExecutor;
    private final HttpClient client;
    public static final int TIMEOUT_EXECUTOR = 10;

    public MyHttpServer(ServiceConfig config) throws IOException {
        super(createConfigFromPort(config.selfPort()));
        client = HttpClient.newHttpClient();
        this.config = config;
        dao = new InMemoryDao(createDaoConfig());
        initExecutor();
    }

    private Config createDaoConfig() {
        return new Config(config.workingDir(), FLUSH_THRESHOLD);
    }

    private void initExecutor() {
        int threadsCount = Runtime.getRuntime().availableProcessors() - 2;
        requestsExecutor = new ThreadPoolExecutor(threadsCount, threadsCount, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(REQUESTS_MAX_QUEUE_SIZE), new ThreadPoolExecutor.AbortPolicy());
        requestsExecutor.prestartAllCoreThreads();
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
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
        session.sendResponse(response);
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (!"/v0/entity".equals(request.getPath())) {
            sendResponse(session, new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        String id = request.getParameter("id=");
        if (id == null || id.isEmpty()) {
            sendResponse(session, new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        try {
            processRequest(request, session, id);
        } catch (RejectedExecutionException e) {
            logger.error("Reject request {} method get", id, e);
            sendResponse(session, new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY));
        }
    }

    private void processRequest(Request request, HttpSession session, String id) {
        requestsExecutor.execute(() -> {
            String localRequest = request.getHeader("local", null);

            if (localRequest != null) {
                try {
                    sendResponse(session, handleRequest(request, id));
                } catch (IOException e) {
                    logger.error("Error while handling request", e);
                    sendResponse(session, new Response(Response.INTERNAL_ERROR, Response.EMPTY));
                }
                return;
            }

            String fromStr = request.getParameter("from=", null);
            String ackStr = request.getParameter("ack=", null);

            int clusterSize = config.clusterUrls().size();
            int from = fromStr == null ? clusterSize : Integer.parseInt(fromStr);
            int ack = ackStr == null ? clusterSize / 2 + 1 : Integer.parseInt(ackStr);
            if (from == 0 || ack == 0 || ack > from || from > clusterSize) {
                sendResponse(session, new Response(Response.BAD_REQUEST, Response.EMPTY));
                return;
            }

            List<HashItem> nodes = getArrayOfNodesFor(config.clusterUrls(), id, from);
            int success = 0;
            Response[] responses = new Response[ack];
            request.addHeader(TIMESTAMP_HEADER + System.currentTimeMillis());
            for (int i = 0; i < from; i++) {
                if (success == ack) {
                    break;
                }
                HashItem node = nodes.get(i);
                try {
                    if (node.url.equals(config.selfUrl())) {
                        responses[success] = handleRequest(request, id);
                    } else {
                        responses[success] = proxyRequest(node.url, request);
                    }
                    success++;
                } catch (IOException e) {
                    logger.error("Exception while proxy request to {}", node.url, e);
                } catch (InterruptedException e) {
                    logger.error("Interrupted while proxy request to {}", node.url, e);
                    Thread.currentThread().interrupt();
                }
            }
            if (success < ack) {
                sendResponse(session, new Response(Responses.NOT_ENOUGH_REPLICAS, Response.EMPTY));
                return;
            }

            if (request.getMethod() == Request.METHOD_GET) {
                int countNotFound = 0;
                long freshestEntryTimestamp = -1;
                Response freshestResponse = null;
                for (Response item : responses) {
                    if (item.getStatus() == HttpURLConnection.HTTP_NOT_FOUND) {
                        countNotFound++;
                        continue;
                    }
                    long timestamp = Long.parseLong(item.getHeader(TIMESTAMP_HEADER));
                    if (timestamp > freshestEntryTimestamp) {
                        freshestEntryTimestamp = timestamp;
                        freshestResponse = item;
                    }
                }
                Response result;
                if (countNotFound == ack
                        || freshestResponse == null
                        || (freshestResponse.getBody().length == 0
                        && freshestResponse.getHeader(X_TOMB_HEADER) != null)) {
                    result = new Response(Response.NOT_FOUND, Response.EMPTY);
                } else {
                    result = new Response(Response.OK, freshestResponse.getBody());
                }
                sendResponse(session, result);
            } else {
                String responseStatusCode = getResponseStatusCode(responses[0].getStatus());
                Response response = new Response(responseStatusCode, responses[0].getBody());
                sendResponse(session, response);
            }

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

    private static List<HashItem> getArrayOfNodesFor(List<String> clusterUrls, String key, Integer from) {
        Queue<HashItem> urls = new PriorityQueue<>(clusterUrls.size());

        for (String url : clusterUrls) {
            int hash = Hash.murmur3(url + key);
            urls.add(new HashItem(url, hash));
        }
        return urls.stream().limit(from).toList();
    }

    private static class HashItem implements Comparable<HashItem> {
        String url;
        int hash;

        public HashItem(String url, int hash) {
            this.url = url;
            this.hash = hash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            HashItem hashItem = (HashItem) o;
            return hash == hashItem.hash;
        }

        @Override
        public int hashCode() {
            return Objects.hash(hash);
        }

        @Override
        public int compareTo(HashItem o) {
            return o.hash - hash;
        }
    }

    private Response handleRequest(Request request, String id) throws IOException {
        String timestamp = request.getHeader(TIMESTAMP_HEADER);
        switch (request.getMethod()) {
            case Request.METHOD_GET -> {
                Entry<String> entry = dao.get(id);
                if (entry == null) {
                    return new Response(Response.NOT_FOUND, Response.EMPTY);
                }
                String value = entry.value();
                Response response;
                if (value == null) {
                    response = new Response(Response.OK, Response.EMPTY);
                    response.addHeader("X-tomb: 1");
                } else {
                    char[] chars = value.toCharArray();
                    response = new Response(Response.OK, Base64.decodeFromChars(chars));
                }
                response.addHeader(TIMESTAMP_HEADER + entry.getTimestamp());
                return response;
            }
            case Request.METHOD_PUT -> {
                byte[] value = request.getBody();
                dao.upsert(new BaseEntry<>(id, new String(Base64.encodeToChars(value)), Long.parseLong(timestamp)));
                return new Response(Response.CREATED, Response.EMPTY);
            }
            case Request.METHOD_DELETE -> {
                dao.upsert(new BaseEntry<>(id, null, Long.parseLong(timestamp)));
                return new Response(Response.ACCEPTED, Response.EMPTY);
            }
            default -> {
                return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            }
        }
    }

    private Response proxyRequest(String url, Request request) throws IOException, InterruptedException {
        HttpRequest proxyRequest = HttpRequest.newBuilder(URI.create(url + request.getURI()))
                .method(
                        request.getMethodName(),
                        request.getBody() == null
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofByteArray(request.getBody())
                )
                .header("local", "true")
                .headers("X-timestamp", request.getHeader(TIMESTAMP_HEADER))
                .build();

        HttpResponse<byte[]> response = client.send(proxyRequest, HttpResponse.BodyHandlers.ofByteArray());
        String status = getResponseStatusCode(response.statusCode());
        Response result = new Response(status, response.body());
        Optional<String> headerTimestamp = response.headers().firstValue("x-timestamp");
        Optional<String> headerTomb = response.headers().firstValue("x-tomb");
        headerTimestamp.ifPresent(s -> result.addHeader(TIMESTAMP_HEADER + s));
        headerTomb.ifPresent(s -> result.addHeader(X_TOMB_HEADER + s));
        return result;
    }

    @Override
    public synchronized void stop() {
        requestsExecutor.shutdown();
        try {
            if (!requestsExecutor.awaitTermination(TIMEOUT_EXECUTOR, TimeUnit.SECONDS)) {
                shutdownNowExecutor();
            }
        } catch (InterruptedException e) {
            logger.error("InterruptedException while stopping requestExecutor", e);
            Thread.currentThread().interrupt();
            shutdownNowExecutor();
        }
        closeSessions();
        super.stop();
        try {
            dao.close();
        } catch (IOException e) {
            logger.error("Error while closing dao", e);
        }
    }

    private void shutdownNowExecutor() {
        List<Runnable> listOfRequests = requestsExecutor.shutdownNow();
        logger.error("Can't stop executor. Shutdown now, number of requests: {}", listOfRequests.size());
    }

    private void closeSessions() {
        for (SelectorThread selector : selectors) {
            if (selector.selector.isOpen()) {
                for (Session session : selector.selector) {
                    session.close();
                }
            }
        }
    }
}
