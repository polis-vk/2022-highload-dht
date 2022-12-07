package ok.dht.test.shik;

import ok.dht.ServiceConfig;
import ok.dht.test.shik.events.HandlerRequest;
import ok.dht.test.shik.events.HandlerResponse;
import ok.dht.test.shik.sharding.ConsistentHash;
import ok.dht.test.shik.sharding.ShardingConfig;
import ok.dht.test.shik.workers.WorkersConfig;
import ok.dht.test.shik.workers.WorkersService;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.BufferOverflowException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.BiConsumer;

public class CustomHttpServer extends HttpServer {

    private static final String CLOSE_CONNECTION_HEADER = "Connection: close";
    private static final String PATH_PREFIX = "/v0/entity";
    private static final String ID_PARAM = "id=";
    private static final String URL_INFIX = PATH_PREFIX + "?" + ID_PARAM;
    private static final Log LOG = LogFactory.getLog(CustomHttpServer.class);
    private static final Map<Integer, String> HTTP_CODE_TO_MESSAGE = Map.of(
        HttpURLConnection.HTTP_OK, Response.OK,
        HttpURLConnection.HTTP_ACCEPTED, Response.ACCEPTED,
        HttpURLConnection.HTTP_CREATED, Response.CREATED,
        HttpURLConnection.HTTP_BAD_REQUEST, Response.BAD_REQUEST,
        HttpURLConnection.HTTP_UNAVAILABLE, Response.SERVICE_UNAVAILABLE,
        HttpURLConnection.HTTP_INTERNAL_ERROR, Response.INTERNAL_ERROR
    );
    private static final int TIMEOUT_MILLIS = 10000;
    private static final int ILLNESS_RATE_MILLIS = 5 * 60 * 1000;
    private static final int FAILURES_THRESHOLD = 5;

    private final WorkersService workersService;
    private final ConsistentHash consistentHash;
    private final String selfUrl;
    private final HttpClient httpClient;
    private final AtomicBoolean[] nodeIllness;
    private final AtomicIntegerArray nodeFailures;
    private final Map<String, Integer> clusterUrlToIndex;

    private CustomService requestHandler;
    private ScheduledThreadPoolExecutor illNodesUpdaterPool;

    public CustomHttpServer(HttpServerConfig config,
                            ServiceConfig serviceConfig,
                            WorkersConfig workersConfig,
                            ShardingConfig shardingConfig,
                            Object... routers) throws IOException {
        super(config, routers);
        workersService = new WorkersService(workersConfig);
        List<String> clusterUrls = serviceConfig.clusterUrls();
        consistentHash = new ConsistentHash(shardingConfig.getVNodesNumber(), clusterUrls);
        httpClient = HttpClient.newBuilder()
            .executor(Executors.newFixedThreadPool(workersConfig.getMaxPoolSize()))
            .build();
        selfUrl = serviceConfig.selfUrl();
        nodeIllness = new AtomicBoolean[clusterUrls.size()];
        nodeFailures = new AtomicIntegerArray(clusterUrls.size());
        clusterUrlToIndex = new HashMap<>(clusterUrls.size());
        for (int i = 0; i < clusterUrls.size(); ++i) {
            clusterUrlToIndex.put(clusterUrls.get(i), i);
        }
    }

    public void setRequestHandler(CustomService requestHandler) {
        this.requestHandler = requestHandler;
    }

    @Override
    public synchronized void start() {
        workersService.start();
        for (int i = 0; i < nodeIllness.length; ++i) {
            nodeIllness[i] = new AtomicBoolean(false);
        }
        illNodesUpdaterPool = new ScheduledThreadPoolExecutor(1);
        illNodesUpdaterPool.scheduleAtFixedRate(() -> {
            for (AtomicBoolean illness : nodeIllness) {
                illness.set(false);
            }
            for (int i = 0; i < nodeFailures.length(); ++i) {
                nodeFailures.set(i, 0);
            }
        }, ILLNESS_RATE_MILLIS, ILLNESS_RATE_MILLIS, TimeUnit.MILLISECONDS);
        super.start();
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        String path = request.getPath();
        if (!path.startsWith(PATH_PREFIX)) {
            sendBadRequest(session);
            return;
        }

        String id = request.getParameter(ID_PARAM);
        if (id == null || id.isEmpty()) {
            sendBadRequest(session);
            return;
        }

        String shardUrl = consistentHash.getShardUrlByKey(id.getBytes(StandardCharsets.UTF_8));
        if (nodeIllness[clusterUrlToIndex.get(shardUrl)].get()) {
            sendResponse(session, new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY));
            return;
        }

        if (selfUrl.equals(shardUrl)) {
            handleCurrentShardRequest(request, session, id);
        } else {
            handleProxyRequest(request, session, id, shardUrl);
        }
    }

    private void handleProxyRequest(Request request, HttpSession session,
                                    String id, String shardUrl) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(shardUrl + URL_INFIX + id));
        HttpRequest httpRequest;
        switch (request.getMethod()) {
            case Request.METHOD_GET -> httpRequest = builder.GET().build();
            case Request.METHOD_PUT -> {
                byte[] body = request.getBody();
                if (body == null) {
                    sendBadRequest(session);
                    return;
                }
                httpRequest = builder.PUT(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            }
            case Request.METHOD_DELETE -> httpRequest = builder.DELETE().build();
            default -> {
                session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
                return;
            }
        }

        sendProxyRequest(httpRequest, session);
    }

    private void sendProxyRequest(HttpRequest httpRequest, HttpSession session) {
        workersService.submitTask(() -> {
            try {
                HttpResponse<byte[]> httpResponse = httpClient
                    .sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray())
                    .get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

                Response failureResponse = checkProxyResponseFailure(httpRequest, httpResponse);
                if (failureResponse != null) {
                    session.sendResponse(failureResponse);
                    return;
                }

                byte[] body = Objects.requireNonNullElse(httpResponse.body(), new byte[0]);
                String statusCode = HTTP_CODE_TO_MESSAGE.get(httpResponse.statusCode());
                if (statusCode == null) {
                    LOG.error("Unexpected error code from other shard: " + httpResponse.statusCode());
                    session.sendResponse(new Response(Response.SERVICE_UNAVAILABLE, body));
                } else {
                    session.sendResponse(new Response(statusCode, body));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("Interrupted while processing proxy request", e);
                sendError(session, e);
            } catch (ExecutionException e) {
                LOG.warn("Execution exception while processing proxy request", e);
                markNodeIll(httpRequest.uri());
                sendError(session, e);
            } catch (TimeoutException e) {
                LOG.warn("Timeout while processing proxy request", e);
                markNodeIll(httpRequest.uri());
                sendError(session, e);
            } catch (IOException e) {
                LOG.warn("I/O exception while processing proxy request", e);
                sendError(session, e);
            }
        });
    }

    private Response checkProxyResponseFailure(HttpRequest httpRequest, HttpResponse<byte[]> httpResponse) {
        if (httpResponse == null || httpResponse.statusCode() == HttpURLConnection.HTTP_UNAVAILABLE) {
            LOG.error("Cannot proxy query, request " + httpRequest.uri().toString());
            return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
        }
        if (httpResponse.statusCode() == HttpURLConnection.HTTP_INTERNAL_ERROR) {
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
        if (httpResponse.statusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }
        return null;
    }

    private void markNodeIll(URI uri) {
        String path = uri.toString();
        String clusterUrl = path.substring(0, path.indexOf(PATH_PREFIX));
        int nodeIndex = clusterUrlToIndex.get(clusterUrl);
        if (nodeFailures.incrementAndGet(nodeIndex) >= FAILURES_THRESHOLD) {
            nodeIllness[nodeIndex].set(true);
        }
    }

    private void handleCurrentShardRequest(Request request, HttpSession session, String id) throws IOException {
        HandlerRequest handlerRequest = new HandlerRequest(request, session, id);
        HandlerResponse handlerResponse = new HandlerResponse();
        try {
            switch (request.getMethod()) {
                case Request.METHOD_GET -> workersService.submitTask(() ->
                    handleConcreteRequest(handlerRequest, handlerResponse, requestHandler::handleGet));
                case Request.METHOD_PUT -> {
                    if (request.getBody() == null) {
                        sendBadRequest(session);
                    }
                    workersService.submitTask(() ->
                        handleConcreteRequest(handlerRequest, handlerResponse, requestHandler::handlePut));
                }
                case Request.METHOD_DELETE -> workersService.submitTask(() ->
                    handleConcreteRequest(handlerRequest, handlerResponse, requestHandler::handleDelete));
                default -> session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
            }
        } catch (RejectedExecutionException e) {
            LOG.warn("Internal executor queue is full", e);
            session.sendResponse(new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY));
        }
    }

    private void handleConcreteRequest(HandlerRequest request, HandlerResponse response,
                                      BiConsumer<HandlerRequest, HandlerResponse> method) {
        try {
            method.accept(request, response);
            sendResponse(request.getSession(), response.getResponse());
        } catch (Exception e) {
            sendError(request.getSession(), e);
        }
    }

    private void sendResponse(HttpSession session, Response response) throws IOException {
        session.sendResponse(response);
    }

    private void sendBadRequest(HttpSession session) throws IOException {
        sendResponse(session, new Response(Response.BAD_REQUEST, Response.EMPTY));
    }

    private void sendError(HttpSession session, Exception e) {
        try {
            String response;
            if (BufferOverflowException.class == e.getClass()) {
                response = Response.REQUEST_ENTITY_TOO_LARGE;
            } else if (TimeoutException.class == e.getClass()) {
                response = Response.GATEWAY_TIMEOUT;
            } else {
                response = Response.SERVICE_UNAVAILABLE;
            }
            session.sendError(response, e.getMessage());
        } catch (IOException e1) {
            LOG.error("Error while sending message about error: ", e1);
        }
    }

    @Override
    public synchronized void stop() {
        for (SelectorThread selector : selectors) {
            if (selector.isAlive()) {
                for (Session session : selector.selector) {
                    Response response = new Response(Response.OK, Response.EMPTY);
                    response.addHeader(CLOSE_CONNECTION_HEADER);
                    byte[] responseBytes = response.toBytes(false);
                    try {
                        session.write(responseBytes, 0, responseBytes.length);
                    } catch (IOException e) {
                        LOG.error("Error while sending client info about closing socket", e);
                    }
                    session.socket().close();
                }
            }
        }
        super.stop();
        workersService.stop();
        stopIllnessPool();
    }

    private void stopIllnessPool() {
        illNodesUpdaterPool.shutdown();
        try {
            if (!illNodesUpdaterPool.awaitTermination(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                illNodesUpdaterPool.shutdownNow();
                if (!illNodesUpdaterPool.awaitTermination(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    LOG.warn("Cannot terminate illness thread pool");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            illNodesUpdaterPool.shutdownNow();
        }
    }

}
