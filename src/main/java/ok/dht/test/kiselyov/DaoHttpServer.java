package ok.dht.test.kiselyov;

import com.google.common.primitives.Bytes;
import ok.dht.ServiceConfig;
import ok.dht.test.kiselyov.dao.BaseEntry;
import ok.dht.test.kiselyov.dao.impl.PersistentDao;
import ok.dht.test.kiselyov.util.ClusterNode;
import ok.dht.test.kiselyov.util.InternalClient;
import ok.dht.test.kiselyov.util.NodeDeterminer;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class DaoHttpServer extends HttpServer {
    private final NodeDeterminer nodeDeterminer;
    private final InternalClient internalClient;
    private final ExecutorService executorService;
    private final PersistentDao dao;

    private final ServiceConfig config;
    public List<Response> responses;
    private static final Logger LOGGER = LoggerFactory.getLogger(DaoHttpServer.class);

    public DaoHttpServer(ServiceConfig config, ExecutorService executorService, PersistentDao dao) throws IOException {
        super(createConfigFromPort(config.selfPort()));
        this.config = config;
        this.executorService = executorService;
        this.dao = dao;
        nodeDeterminer = new NodeDeterminer(config.clusterUrls());
        internalClient = new InternalClient();
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        String id = request.getParameter("id=");
        if (id == null || id.isBlank()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }
        int ack;
        int from;
        String fromParam = request.getParameter("from=");
        if (fromParam == null) {
            from = config.clusterUrls().size();
            ack = from / 2 + 1;
        } else {
            from = Integer.parseInt(fromParam);
            ack = Integer.parseInt(request.getParameter("ack="));
        }
        if (ack > from || ack == 0) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }
        String fromCoordinator = request.getHeader("fromCoordinator");
        executorService.execute(() -> {
            try {
                if (fromCoordinator == null) {
                    coordinateRequest(request, session, id, ack, from);
                } else {
                    switch (request.getMethodName()) {
                        case "PUT" -> session.sendResponse(handlePut(id, request));
                        case "GET" -> session.sendResponse(handleGet(id));
                        case "DELETE" -> session.sendResponse(handleDelete(id));
                        default -> {
                            LOGGER.error("Unsupported request method: {}", request.getMethodName());
                            handleDefault(request, session);
                            throw new UnsupportedOperationException("Unsupported request method: "
                                    + request.getMethodName());
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Error handling request.", e);
            }
        });
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        String resultCode = request.getMethod() == Request.METHOD_GET
                || request.getMethod() == Request.METHOD_PUT
                ? Response.BAD_REQUEST : Response.METHOD_NOT_ALLOWED;
        Response defaultResponse = new Response(resultCode, Response.EMPTY);
        session.sendResponse(defaultResponse);
    }

    @Override
    public synchronized void stop() {
        for (SelectorThread selectorThread : selectors) {
            if (selectorThread.isAlive()) {
                for (Session session : selectorThread.selector) {
                    session.socket().close();
                }
            }
        }
        super.stop();
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response handleGet(@Param(value = "id") String id) {
        BaseEntry<byte[], Long> result;
        try {
            result = dao.get(id.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            LOGGER.error("GET operation with id {} from GET request failed.", id, e);
            return new Response(Response.INTERNAL_ERROR, e.getMessage().getBytes(StandardCharsets.UTF_8));
        }
        if (result == null) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }
        ByteBuffer timestamp = ByteBuffer.allocate(Long.BYTES);
        timestamp.putLong(result.timestamp());
        ByteBuffer tombstone = ByteBuffer.allocate(Integer.BYTES);
        tombstone.putInt(-1);
        if (result.value() == null) {
            return new Response(Response.OK, Bytes.concat(timestamp.array(), tombstone.array()));
        }
        return new Response(Response.OK, Bytes.concat(timestamp.array(), result.value()));
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(@Param(value = "id") String id, Request putRequest) {
        try {
            long timestamp = System.currentTimeMillis();
            dao.upsert(new BaseEntry<>(id.getBytes(StandardCharsets.UTF_8), putRequest.getBody(), timestamp));
        } catch (Exception e) {
            LOGGER.error("UPSERT operation with id {} from PUT request failed.", id, e);
            return new Response(Response.INTERNAL_ERROR, e.getMessage().getBytes(StandardCharsets.UTF_8));
        }
        return new Response(Response.CREATED, Response.EMPTY);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(@Param(value = "id") String id) {
        try {
            long timestamp = System.currentTimeMillis();
            dao.upsert(new BaseEntry<>(id.getBytes(StandardCharsets.UTF_8), null, timestamp));
        } catch (Exception e) {
            LOGGER.error("UPSERT operation with id {} from DELETE request failed.", id, e);
            return new Response(Response.INTERNAL_ERROR, e.getMessage().getBytes(StandardCharsets.UTF_8));
        }
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    private void coordinateRequest(Request request, HttpSession session, String id, int ack, int from)
            throws IOException {
        responses = new ArrayList<>(from);
        for (ClusterNode targetClusterNode : nodeDeterminer.getNodeUrls(id, from)) {
            if (targetClusterNode.hasUrl(config.selfUrl())) {
                switch (request.getMethodName()) {
                    case "PUT" -> responses.add(handlePut(id, request));
                    case "GET" -> responses.add(handleGet(id));
                    case "DELETE" -> responses.add(handleDelete(id));
                    default -> {
                        LOGGER.error("Unsupported request method: {}", request.getMethodName());
                        handleDefault(request, session);
                        throw new UnsupportedOperationException("Unsupported request method: "
                                + request.getMethodName());
                    }
                }
            } else {
                responses.add(sendResponse(request, id, targetClusterNode));
            }
        }
        replicationDecision(responses, ack, request.getMethodName(), session);
    }

    private Response sendResponse(Request request, String id, ClusterNode targetClusterNode) {
        HttpResponse<byte[]> getResponse;
        try {
            request.addHeader("fromCoordinator 1");
            getResponse = internalClient.sendRequestToNode(request, targetClusterNode, id);
            switch (request.getMethod()) {
                case Request.METHOD_GET -> {
                    if (getResponse.statusCode() == HttpURLConnection.HTTP_OK) {
                        return new Response(getResponseCode(getResponse.statusCode()), getResponse.body());
                    } else {
                        return new Response(getResponseCode(getResponse.statusCode()), Response.EMPTY);
                    }
                }
                case Request.METHOD_PUT, Request.METHOD_DELETE -> {
                    return new Response(getResponseCode(getResponse.statusCode()), Response.EMPTY);
                }
                default -> {
                    LOGGER.error("Unsupported request method: {}", request.getMethodName());
                    throw new UnsupportedOperationException("Unsupported request method: "
                            + request.getMethodName());
                }
            }
        } catch (URISyntaxException e) {
            LOGGER.error("URI error.", e);
            return new Response(Response.BAD_GATEWAY, Response.EMPTY);
        } catch (IOException e) {
            LOGGER.error("Error handling request.", e);
            return new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY);
        } catch (InterruptedException e) {
            LOGGER.error("Error while getting response.", e);
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }

    private void replicationDecision(List<Response> responses, int ack, String method, HttpSession session) {
        int successResponses = 0;
        for (Response response : responses) {
            int responseStatus = response.getStatus();
            if ((responseStatus >= 200 && responseStatus <= 202) || responseStatus == 404) {
                successResponses++;
            }
        }
        try {
            switch (method) {
                case "GET" -> {
                    byte[] body = getBody(responses);
                    if (successResponses >= ack && body != null) {
                        session.sendResponse(new Response(Response.OK, body));
                    } else if (successResponses >= ack) {
                        session.sendResponse(new Response(Response.NOT_FOUND, Response.EMPTY));
                    } else {
                        session.sendResponse(new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY));
                    }
                }
                case "PUT" -> {
                    if (successResponses >= ack) {
                        session.sendResponse(new Response(Response.CREATED, Response.EMPTY));
                    } else {
                        session.sendResponse(new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY));
                    }
                }
                case "DELETE" -> {
                    if (successResponses >= ack) {
                        session.sendResponse(new Response(Response.ACCEPTED, Response.EMPTY));
                    } else {
                        session.sendResponse(new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY));
                    }
                }
                default -> {
                    LOGGER.error("Unsupported request method: {}", method);
                    session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error handling request.", e);
        }
    }

    private byte[] getBody(List<Response> responses) {
        byte[] body = null;
        long maxTimestamp = -1L;
        for (Response response : responses) {
            if (response.getStatus() == 200) {
                ByteBuffer buffer = ByteBuffer.wrap(response.getBody());
                long currentTimestamp = buffer.getLong();
                if (currentTimestamp > maxTimestamp) {
                    buffer.position(Long.BYTES);
                    body = new byte[0];
                    while (buffer.position() < buffer.capacity()) {
                        body = Bytes.concat(body, new byte[] {buffer.get()});
                    }
                    if (body.length == Integer.BYTES && ByteBuffer.wrap(body).getInt() == -1) {
                        body = null;
                    }
                    maxTimestamp = currentTimestamp;
                }
            }
        }
        return body;
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    private static String getResponseCode(int statusCode) {
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
            default -> throw new IllegalArgumentException("No such status code: " + statusCode);
        };
    }
}
