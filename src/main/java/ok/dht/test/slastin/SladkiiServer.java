package ok.dht.test.slastin;

import ok.dht.ServiceConfig;
import ok.dht.test.slastin.range.SladkiiSession;
import ok.dht.test.slastin.replication.ReplicasDeleteRequestHandler;
import ok.dht.test.slastin.replication.ReplicasGetRequestHandler;
import ok.dht.test.slastin.replication.ReplicasPutRequestHandler;
import ok.dht.test.slastin.sharding.ShardingManager;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestHandler;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.server.RejectedSessionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static ok.dht.test.slastin.Utils.badMethod;
import static ok.dht.test.slastin.Utils.badRequest;
import static ok.dht.test.slastin.Utils.getResponseCodeByStatusCode;
import static ok.dht.test.slastin.Utils.serviceUnavailable;

public class SladkiiServer extends HttpServer {
    private static final Logger log = LoggerFactory.getLogger(SladkiiServer.class);
    private static final Duration PROXY_REQUEST_DURATION = Duration.ofSeconds(1);

    private final ServiceConfig serviceConfig;
    private final int selfNodeIndex;
    private final SladkiiComponent component;
    private final ShardingManager shardingManager;
    private final ExecutorService heavyExecutor;
    private final ExecutorService lightExecutor;
    private final HttpClient client;

    public SladkiiServer(
            HttpServerConfig httpServerConfig,
            ServiceConfig serviceConfig,
            SladkiiComponent component,
            ShardingManager shardingManager,
            ExecutorService heavyExecutor,
            ExecutorService lightExecutor,
            ExecutorService httpClientExecutor
    ) throws IOException {
        super(httpServerConfig);
        this.serviceConfig = serviceConfig;
        this.selfNodeIndex = serviceConfig.clusterUrls().indexOf(serviceConfig.selfUrl());
        this.component = component;
        this.shardingManager = shardingManager;
        this.heavyExecutor = heavyExecutor;
        this.lightExecutor = lightExecutor;
        client = HttpClient.newBuilder()
                .executor(httpClientExecutor)
                .build();
    }

    @Path("/v0/internal/entity")
    public void handleInternalRequest(Request request, HttpSession session) {
        // supposes that previous node checked client's id
        String id = request.getParameter("id=");

        // log.info("internal handling {} for id {}", request.getMethodName(), id);

        try {
            futureComponentRequest(id, request)
                    // it's ok to send response in the current selector thread if db is quick
                    .thenAccept(response -> sendResponse(session, response));
        } catch (RejectedExecutionException e) {
            log.error("Can not schedule task for execution", e);
            sendResponse(session, serviceUnavailable());
        }
    }

    @Path("/v0/entity")
    public void handleClientRequest(Request request, HttpSession session) throws IOException {
        String id = request.getParameter("id=");
        if (id == null || id.isBlank()) {
            sendResponse(session, badRequest());
            return;
        }

        int ack;
        int from;
        try {
            String ackParameter = request.getParameter("ack=");
            if (ackParameter == null) {
                from = serviceConfig.clusterUrls().size();
                ack = from / 2 + 1;
            } else {
                from = Integer.parseInt(request.getParameter("from="));
                ack = Integer.parseInt(ackParameter);
            }
        } catch (NumberFormatException e) {
            sendResponse(session, badRequest());
            return;
        }

        if (!validateAckFrom(ack, from)) {
            sendResponse(session, badRequest());
            return;
        }

        // log.info("handling {} for id {}", request.getMethodName(), id);

        RequestHandler replicasRequestHandler = switch (request.getMethod()) {
            case Request.METHOD_GET -> new ReplicasGetRequestHandler(id, ack, from, this);
            case Request.METHOD_PUT -> new ReplicasPutRequestHandler(id, ack, from, this);
            case Request.METHOD_DELETE -> new ReplicasDeleteRequestHandler(id, ack, from, this);
            default -> {
                sendResponse(session, badMethod());
                yield null;
            }
        };

        if (replicasRequestHandler != null) {
            replicasRequestHandler.handleRequest(request, session);
        }
    }

    @Path("/v0/entities")
    public void handleRangeRequest(Request request, HttpSession session) {
        String start = request.getParameter("start=");
        if (start == null || start.isBlank()) {
            sendResponse(session, badRequest());
            return;
        }

        String end = request.getParameter("end=");

        // log.info("handling range request for start = {}, end = {}", start, end);

        try {
            CompletableFuture.supplyAsync(() -> component.range(start, end), heavyExecutor)
                    .thenAccept(rangeResponse -> sendResponse(session, rangeResponse));
        } catch (RejectedExecutionException e) {
            log.error("Can not schedule task for execution", e);
            sendResponse(session, serviceUnavailable());
        }
    }

    @Override
    public void handleDefault(Request request, HttpSession session) {
        sendResponse(session, badRequest());
    }

    @Override
    public HttpSession createSession(Socket socket) throws RejectedSessionException {
        return new SladkiiSession(socket, this);
    }

    private boolean validateAckFrom(int ack, int from) {
        return 0 < ack && ack <= from && from <= serviceConfig.clusterUrls().size();
    }

    public ShardingManager getShardingManager() {
        return shardingManager;
    }

    public CompletableFuture<Response> futureRequest(int nodeIndex, String id, Request request) {
        return nodeIndex == selfNodeIndex
                ? futureComponentRequest(id, request)
                : futureProxyRequest(shardingManager.getNodeUrlByNodeIndex(nodeIndex), id, request);
    }

    private CompletableFuture<Response> futureComponentRequest(String id, Request request) {
        return CompletableFuture.supplyAsync(() -> processComponentRequest(id, request), heavyExecutor);
    }

    private Response processComponentRequest(String id, Request request) {
        return switch (request.getMethod()) {
            case Request.METHOD_GET -> component.get(id);
            case Request.METHOD_PUT -> component.put(id, extractTimestamp(request), request);
            case Request.METHOD_DELETE -> component.delete(id, extractTimestamp(request));
            default -> {
                log.error("unsupported method={}", request.getMethod());
                yield badMethod();
            }
        };
    }

    private static Long extractTimestamp(Request request) {
        return Long.parseLong(request.getHeader("Timestamp:"));
    }

    private CompletableFuture<Response> futureProxyRequest(String nodeUrl, String id, Request request) {
        CompletableFuture<Response> start = new CompletableFuture<>();

        CompletableFuture<Response> end = start.thenApply(x -> makeProxyRequest(nodeUrl, id, request))
                .thenCompose(httpRequest -> client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray()))
                .thenApplyAsync(httpResponse -> new Response(
                                getResponseCodeByStatusCode(httpResponse.statusCode()), httpResponse.body()
                        ), lightExecutor
                )
                .exceptionally(e -> {
                    log.error("error while proxy request", e);
                    return serviceUnavailable();
                });

        start.completeAsync(() -> null, lightExecutor); // trigger execution

        return end;
    }

    private static HttpRequest makeProxyRequest(String nodeUrl, String id, Request request) {
        var builder = HttpRequest.newBuilder(URI.create(nodeUrl + "/v0/internal/entity?id=" + id));

        builder.timeout(PROXY_REQUEST_DURATION);

        var bodyPublishers = request.getBody() == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(request.getBody());
        builder.method(request.getMethodName(), bodyPublishers);

        String timestamp = request.getHeader("Timestamp:");
        if (timestamp != null) {
            builder.setHeader("Timestamp", timestamp);
        }

        return builder.build();
    }

    public void sendResponse(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e) {
            log.error("failed to send response", e);
            closeSession(session);
        }
    }

    @Override
    public synchronized void stop() {
        closeAllSessions();
        super.stop();
    }

    private void closeAllSessions() {
        for (var selectorThread : selectors) {
            selectorThread.selector.forEach(SladkiiServer::closeSession);
        }
    }

    private static void closeSession(Session session) {
        try {
            session.close();
        } catch (Exception e) {
            log.error("failed to close session", e);
        }
    }
}
