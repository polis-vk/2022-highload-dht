package ok.dht.test.shik;

import ok.dht.ServiceConfig;
import ok.dht.test.shik.events.FollowerRequestState;
import ok.dht.test.shik.events.HandlerRequest;
import ok.dht.test.shik.events.HandlerResponse;
import ok.dht.test.shik.events.HandlerTimedRequest;
import ok.dht.test.shik.events.LeaderRequestState;
import ok.dht.test.shik.events.RequestState;
import ok.dht.test.shik.illness.IllNodesService;
import ok.dht.test.shik.sharding.ConsistentHash;
import ok.dht.test.shik.sharding.ShardingConfig;
import ok.dht.test.shik.utils.HttpServerUtils;
import ok.dht.test.shik.validator.ValidationResult;
import ok.dht.test.shik.validator.Validator;
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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CustomHttpServer extends HttpServer {

    private static final String CLOSE_CONNECTION_HEADER = "Connection: close";
    private static final String PATH_PREFIX = "/v0/entity";
    private static final String ID_PARAM = "id=";
    private static final String URL_INFIX = PATH_PREFIX + "?" + ID_PARAM;
    private static final String INTERNAL_HEADER = "Internal-Request";
    private static final String TRUE = "true";
    private static final Log LOG = LogFactory.getLog(CustomHttpServer.class);
    private static final Map<Integer, String> HTTP_CODE_TO_MESSAGE = Map.of(
        HttpURLConnection.HTTP_OK, Response.OK,
        HttpURLConnection.HTTP_ACCEPTED, Response.ACCEPTED,
        HttpURLConnection.HTTP_CREATED, Response.CREATED,
        HttpURLConnection.HTTP_BAD_REQUEST, Response.BAD_REQUEST,
        HttpURLConnection.HTTP_UNAVAILABLE, Response.SERVICE_UNAVAILABLE,
        HttpURLConnection.HTTP_INTERNAL_ERROR, Response.INTERNAL_ERROR,
        HttpURLConnection.HTTP_BAD_METHOD, Response.METHOD_NOT_ALLOWED,
        HttpURLConnection.HTTP_NOT_FOUND, Response.NOT_FOUND
    );

    private static final int TIMEOUT_MILLIS = 10000;
    private static final String NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";

    private final WorkersService workersService;
    private final IllNodesService illNodesService;
    private final ConsistentHash consistentHash;
    private final String selfUrl;
    private final HttpClient httpClient;
    private final Validator validator;

    private CustomService requestHandler;

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
        illNodesService = new IllNodesService(clusterUrls);
        validator = new Validator((clusterUrls.size() + 1) / 2);
    }

    public void setRequestHandler(CustomService requestHandler) {
        this.requestHandler = requestHandler;
    }

    @Override
    public synchronized void start() {
        workersService.start();
        illNodesService.start();
        super.start();
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        ValidationResult params = validator.validate(request);
        if (params.getCode() == HttpURLConnection.HTTP_BAD_REQUEST) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        } else if (params.getCode() == HttpURLConnection.HTTP_BAD_METHOD) {
            session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
        }
        String id = params.getId();
        int requestedReplicas = params.getRequestedReplicas();
        int requiredReplicas = params.getRequiredReplicas();

        if (request.getHeader(INTERNAL_HEADER) != null) {
            FollowerRequestState state = new FollowerRequestState(request, session, request.getParameter(ID_PARAM));
            handleCurrentShardRequest(state);
            session.sendResponse(state.getResponse());
            return;
        }

        List<String> shardUrls = consistentHash.getShardUrlByKey(
            id.getBytes(StandardCharsets.UTF_8), requestedReplicas);
        if (illNodesService.getIllNodesCount(shardUrls) + requiredReplicas > requestedReplicas) {
            session.sendResponse(new Response(NOT_ENOUGH_REPLICAS, Response.EMPTY));
            return;
        }

        RequestState state = new LeaderRequestState(requestedReplicas, requiredReplicas, request, session, id);
        for (String shardUrl : shardUrls) {
            if (!illNodesService.isIllNode(shardUrl)) {
                if (selfUrl.equals(shardUrl)) {
                    handleCurrentShardRequest(state);
                } else {
                    handleProxyRequest(state, shardUrl);
                }
            }
        }

        handleLeaderRequest(state);
    }

    private void handleLeaderRequest(RequestState state) {
        workersService.submitTask(() -> {
            HandlerResponse handlerResponse = new HandlerResponse();
            HandlerRequest handlerRequest = new HandlerRequest(state);
            state.awaitShardResponses();
            Request request = state.getRequest();
            switch (request.getMethod()) {
                case Request.METHOD_GET -> requestHandler.handleLeaderGet(handlerRequest, handlerResponse);
                case Request.METHOD_PUT -> requestHandler.handleLeaderPut(handlerRequest, handlerResponse);
                case Request.METHOD_DELETE -> requestHandler.handleLeaderDelete(handlerRequest, handlerResponse);
                default -> throw new IllegalStateException("Expected one of supported methods");
            }
            try {
                state.getSession().sendResponse(handlerResponse.getResponse());
            } catch (IOException e) {
                LOG.error("Cannot send response ", e);
                HttpServerUtils.sendError(state.getSession(), e);
            }
        });
    }

    private void handleProxyRequest(RequestState state, String shardUrl) {
        Request request = state.getRequest();
        HttpRequest.Builder builder = HttpRequest
            .newBuilder(URI.create(shardUrl + URL_INFIX + state.getId()))
            .header(INTERNAL_HEADER, TRUE);
        HttpRequest httpRequest;
        switch (request.getMethod()) {
            case Request.METHOD_GET -> httpRequest = builder.GET().build();
            case Request.METHOD_PUT ->
                httpRequest = builder.PUT(HttpRequest.BodyPublishers.ofByteArray(request.getBody())).build();
            case Request.METHOD_DELETE -> httpRequest = builder.DELETE().build();
            default -> throw new IllegalStateException("Expected one of supported methods");
        }

        sendProxyRequest(httpRequest, state);
    }

    private void sendProxyRequest(HttpRequest httpRequest, RequestState state) {
        workersService.submitTask(() -> {
            try {
                HttpResponse<byte[]> httpResponse = httpClient
                    .sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray())
                    .get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

                Response failureResponse = checkProxyResponseFailure(httpRequest, httpResponse);
                if (failureResponse != null) {
                    state.onShardResponseFailure();
                    return;
                }

                byte[] body = Objects.requireNonNullElse(httpResponse.body(), new byte[0]);
                String statusCode = HTTP_CODE_TO_MESSAGE.get(httpResponse.statusCode());
                if (statusCode == null) {
                    LOG.error("Unexpected error code from other shard: " + httpResponse.statusCode());
                    state.onShardResponseFailure();
                } else {
                    state.onShardResponseSuccess(new Response(statusCode, body));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("Interrupted while processing proxy request", e);
                state.onShardResponseFailure();
            } catch (ExecutionException e) {
                LOG.warn("Execution exception while processing proxy request", e);
                illNodesService.markNodeIll(httpRequest.uri());
                state.onShardResponseFailure();
            } catch (TimeoutException e) {
                LOG.warn("Timeout while processing proxy request", e);
                illNodesService.markNodeIll(httpRequest.uri());
                state.onShardResponseFailure();
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
        return null;
    }

    private void handleCurrentShardRequest(RequestState state) {
        HandlerResponse handlerResponse = new HandlerResponse();
        Request request = state.getRequest();
        try {
            switch (request.getMethod()) {
                case Request.METHOD_GET -> workersService.submitTask(
                    () -> HttpServerUtils.handleConcreteRequest(state,
                        new HandlerRequest(state),
                        handlerResponse,
                        requestHandler::handleGet
                ));
                case Request.METHOD_PUT -> workersService.submitTask(
                    () -> HttpServerUtils.handleConcreteRequest(state,
                        new HandlerTimedRequest(state, System.currentTimeMillis()),
                        handlerResponse,
                        requestHandler::handlePut
                ));
                case Request.METHOD_DELETE -> workersService.submitTask(
                    () -> HttpServerUtils.handleConcreteRequest(state,
                        new HandlerTimedRequest(state, System.currentTimeMillis()),
                        handlerResponse,
                        requestHandler::handleDelete
                ));
                default -> throw new IllegalStateException("Expected one of supported methods");
            }
        } catch (RejectedExecutionException e) {
            LOG.warn("Internal executor queue is full", e);
            state.onShardResponseFailure();
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
        illNodesService.stop();
    }

}
