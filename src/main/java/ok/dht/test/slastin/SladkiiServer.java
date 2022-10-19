package ok.dht.test.slastin;

import ok.dht.ServiceConfig;
import ok.dht.test.slastin.node.Node;
import ok.dht.test.slastin.sharding.ShardingManager;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.PathMapper;
import one.nio.http.Request;
import one.nio.http.RequestHandler;
import one.nio.http.Response;
import one.nio.net.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static ok.dht.test.slastin.Utils.getResponseCodeByStatusCode;

public class SladkiiServer extends HttpServer {
    private static final Logger log = LoggerFactory.getLogger(SladkiiServer.class);

    private final PathMapper defaultMapper;
    private final ServiceConfig serviceConfig;
    private final List<Node> nodes;
    private final SladkiiComponent component;
    private final ExecutorService processors;
    private final ShardingManager shardingManager;
    private final HttpClient client;

    public SladkiiServer(
            HttpServerConfig httpServerConfig,
            ServiceConfig serviceConfig,
            List<Node> nodes,
            SladkiiComponent component,
            ExecutorService processors,
            ShardingManager shardingManager
    ) throws IOException {
        super(httpServerConfig);
        defaultMapper = extractDefaultMapper();
        this.serviceConfig = serviceConfig;
        this.nodes = nodes;
        this.component = component;
        this.processors = processors;
        this.shardingManager = shardingManager;
        client = HttpClient.newHttpClient();
    }

    private PathMapper extractDefaultMapper() {
        try {
            Field field = HttpServer.class.getDeclaredField("defaultMapper");
            field.setAccessible(true);
            return (PathMapper) field.get(this);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.error("error occurred while extracting default mapper", e);
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        RequestHandler handler = defaultMapper.find(request.getPath(), request.getMethod());
        if (handler == null) {
            handleDefault(request, session);
            return;
        }

        String id = request.getParameter("id=");
        if (id == null) {
            sendResponse(session, badRequest());
            return;
        }

//        log.info("handling {} for id {}", request.getMethodName(), id);

        String currentNodeUrl = shardingManager.getNodeUrlByKey(id);
        int currentNodeIndex = serviceConfig.clusterUrls().indexOf(currentNodeUrl);

        boolean wasTaskAdded = nodes.get(currentNodeIndex).offerTask(() -> {
            try {
                processRequest(currentNodeUrl, id, request, session);
            } catch (Exception e) {
                log.error("Exception occurred while handling request", e);
                sendResponse(session, internalError());
            }
        });

        if (!wasTaskAdded) {
            sendResponse(session, serviceUnavailable());
            return;
        }

        try {
            processors.submit(() -> {
                int nodeIndex = currentNodeIndex;
                while (true) {
                    var node = nodes.get(nodeIndex);
                    var task = node.pollTask();
                    if (task != null) {
                        try {
                            task.run();
                        } finally {
                            node.finishTask();
                        }
                        return;
                    }
                    nodeIndex = (nodeIndex + 1) % nodes.size();
                }
            });
        } catch (RejectedExecutionException e) {
            log.error("Can not schedule task for execution", e);
            sendResponse(session, serviceUnavailable());
        }
    }

    @Override
    public void handleDefault(Request request, HttpSession session) {
        sendResponse(session, badRequest());
    }

    private void processRequest(String nodeUrl, String id, Request request, HttpSession session) {
        if (nodeUrl.equals(serviceConfig.selfUrl())) {
            sendResponse(session, processRequestSelf(id, request));
        } else {
            processRequestProxy(nodeUrl, request, session);
        }
    }

    @Path("/v0/entity")
    public Response processRequestSelf(@Param(value = "id", required = true) String id, Request request) {
        if (id.isBlank()) {
            return badRequest();
        }
        return switch (request.getMethod()) {
            case Request.METHOD_GET -> component.get(id);
            case Request.METHOD_PUT -> component.put(id, request);
            case Request.METHOD_DELETE -> component.delete(id);
            default -> badMethod();
        };
    }

    private void processRequestProxy(String nodeUrl, Request request, HttpSession session) {
        var builder = HttpRequest.newBuilder(URI.create(nodeUrl + request.getURI()));
        var bodyPublishers = request.getBody() == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(request.getBody());
        builder.method(request.getMethodName(), bodyPublishers);

        try {
            var httpResponse = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            var response = new Response(getResponseCodeByStatusCode(httpResponse.statusCode()), httpResponse.body());
            sendResponse(session, response);
        } catch (IOException e) {
            log.error("can not reach {}", nodeUrl, e);
            sendResponse(session, serviceUnavailable());
        } catch (InterruptedException e) {
            log.error("error occurred while handling http response", e);
        }
    }

    private void sendResponse(HttpSession session, Response response) {
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
            selectorThread.selector.forEach(this::closeSession);
        }
    }

    private void closeSession(Session session) {
        try {
            session.close();
        } catch (Exception e) {
            log.error("failed to close session", e);
        }
    }

    static Response serviceUnavailable() {
        return new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
    }

    static Response badMethod() {
        return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
    }

    static Response badRequest() {
        return new Response(Response.BAD_REQUEST, Response.EMPTY);
    }

    static Response internalError() {
        return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
    }

    static Response notFound() {
        return new Response(Response.NOT_FOUND, Response.EMPTY);
    }

    static Response created() {
        return new Response(Response.CREATED, Response.EMPTY);
    }

    static Response accepted() {
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }
}
