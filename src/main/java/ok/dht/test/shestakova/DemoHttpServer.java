package ok.dht.test.shestakova;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.shestakova.dao.MemorySegmentDao;
import ok.dht.test.shestakova.dao.base.BaseEntry;
import ok.dht.test.shestakova.exceptions.MethodNotAllowedException;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;
import one.nio.util.Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class DemoHttpServer extends HttpServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoHttpServer.class);
    private static final String RESPONSE_NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";
    private static final int NOT_FOUND_CODE = 404;
    private final HttpClient httpClient;
    private final ServiceConfig serviceConfig;
    private final ExecutorService workersPool;
    private final CircuitBreakerImpl circuitBreaker;
    private final MemorySegmentDao dao;

    public DemoHttpServer(HttpServerConfig config, HttpClient httpClient, ExecutorService workersPool,
                          ServiceConfig serviceConfig, MemorySegmentDao dao, Object... routers) throws IOException {
        super(config, routers);
        this.httpClient = httpClient;
        this.serviceConfig = serviceConfig;
        this.workersPool = workersPool;
        this.circuitBreaker = new CircuitBreakerImpl(serviceConfig, httpClient);
        this.dao = dao;
    }

    @Override
    public void handleRequest(Request request, HttpSession session) {
        String key = request.getParameter("id=");
        if (key == null || key.isEmpty()) {
            tryToSendResponseWithEmptyBody(session, Response.BAD_REQUEST);
            return;
        }

        String fromString = request.getParameter("from=");
        String ackString = request.getParameter("ack=");
        int from = fromString == null || fromString.isEmpty() ? serviceConfig.clusterUrls().size()
                : Integer.parseInt(fromString);
        int ack = ackString == null || ackString.isEmpty() ? from / 2 + 1 : Integer.parseInt(ackString);

        if (ack == 0 || ack > from || from > serviceConfig.clusterUrls().size()) {
            tryToSendResponseWithEmptyBody(session, Response.BAD_REQUEST);
            return;
        }

        List<String> targetNodes = getClustersByRendezvousHashing(key, ack);
        workersPool.execute(() -> {
            try {
                if (request.getHeader("internal") != null || request.getPath().contains("/service/message")) {
                    Response response = handleInternalRequest(request, session);
                    if (response != null) {
                        session.sendResponse(response);
                    }
                    return;
                }

                List<HttpRequest> httpRequests = getHttpRequests(request, key, targetNodes);
                List<Response> httpResponses = getResponses(request, session, ack, httpRequests);

                if (httpResponses.size() < ack) {
                    tryToSendResponseWithEmptyBody(session, RESPONSE_NOT_ENOUGH_REPLICAS);
                    return;
                }

                if (request.getMethod() != Request.METHOD_GET) {
                    session.sendResponse(httpResponses.get(0));
                    return;
                }

                byte[] body = null;
                int notFoundResponsesCount = 0;
                long maxTimestamp = Long.MIN_VALUE;
                for (Response response : httpResponses) {
                    if (response.getStatus() == NOT_FOUND_CODE) {
                        notFoundResponsesCount++;
                        continue;
                    }
                    ByteBuffer bodyBB = ByteBuffer.wrap(response.getBody());
                    long timestamp = bodyBB.getLong();
                    if (maxTimestamp < timestamp) {
                        maxTimestamp = timestamp;
                        body = getBody(bodyBB);
                    }
                }

                boolean cond = body != null && notFoundResponsesCount != httpResponses.size();
                session.sendResponse(new Response(
                        cond ? Response.OK : Response.NOT_FOUND,
                        cond ? body : Response.EMPTY
                ));
            } catch (MethodNotAllowedException e) {
                LOGGER.error("Method not allowed {} method: {}", serviceConfig.selfUrl(), request.getMethod());
                tryToSendResponseWithEmptyBody(session, Response.METHOD_NOT_ALLOWED);
            } catch (InterruptedException | IOException e) {
                LOGGER.error("Internal error in server {}", serviceConfig.selfUrl());
                tryToSendResponseWithEmptyBody(session, Response.INTERNAL_ERROR);
            }
        });
    }

    private List<HttpRequest> getHttpRequests(Request request, String key, List<String> targetNodes) {
        List<HttpRequest> httpRequests = new ArrayList<>();
        for (String node : targetNodes) {
            if (node.equals(serviceConfig.selfUrl())) {
                httpRequests.add(null);
                continue;
            }
            HttpRequest tmpRequest = buildHttpRequest(key, node, request);
            httpRequests.add(tmpRequest);
        }
        return httpRequests;
    }

    private List<Response> getResponses(Request request, HttpSession session, int ack, List<HttpRequest> httpRequests)
            throws InterruptedException {
        List<Response> httpResponses = new ArrayList<>();
        for (HttpRequest httpRequest : httpRequests) {
            if (httpResponses.size() == ack) {
                break;
            }
            if (httpRequest == null) {
                Response response = handleInternalRequest(request, session);
                httpResponses.add(response);
                continue;
            }
            CompletableFuture<HttpResponse<byte[]>> responseCompletableFuture = httpClient
                    .sendAsync(
                            httpRequest,
                            HttpResponse.BodyHandlers.ofByteArray()
                    );
            HttpResponse<byte[]> httpResponse = getResponseOrNull(responseCompletableFuture);
            if (httpResponse != null) {
                httpResponses.add(new Response(
                        StatusCodes.statuses.getOrDefault(httpResponse.statusCode(), "UNKNOWN ERROR"),
                        httpResponse.body()
                ));
            }
        }
        return httpResponses;
    }

    private byte[] getBody(ByteBuffer bodyBB) {
        byte[] body;
        bodyBB.position(Long.BYTES);
        int valueLength = bodyBB.getInt();
        if (valueLength == -1) {
            body = null;
        } else {
            body = new byte[valueLength];
            bodyBB.get(body, 0, valueLength);
        }
        return body;
    }

    private void tryToSendResponseWithEmptyBody(HttpSession session, String resultCode) {
        try {
            session.sendResponse(new Response(
                    resultCode,
                    Response.EMPTY
            ));
        } catch (IOException e) {
            LOGGER.error("Error while sending response in server {}", serviceConfig.selfUrl());
        }
    }

    private Response handleInternalRequest(Request request, HttpSession session) {
        int methodNum = request.getMethod();
        Response response;
        String id = request.getParameter("id");
        if (methodNum == Request.METHOD_GET) {
            response = handleGet(id);
        } else if (methodNum == Request.METHOD_PUT) {
            String requestPath = request.getPath();
            if (requestPath.contains("/service/message")) {
                putNodesIllnessInfo(Arrays.toString(request.getBody()), requestPath.equals("/service/message/ill"));
                tryToSendResponseWithEmptyBody(session, Response.SERVICE_UNAVAILABLE);
                return null;
            }
            response = handlePut(request, id);
        } else if (methodNum == Request.METHOD_DELETE) {
            response = handleDelete(id);
        } else {
            response = new Response(
                    Response.METHOD_NOT_ALLOWED,
                    Response.EMPTY
            );
        }
        return response;
    }

    private Response handleGet(@Param(value = "id") String id) {
        BaseEntry<MemorySegment> entry = dao.get(fromString(id));
        if (entry == null) {
            return new Response(
                    Response.NOT_FOUND,
                    Response.EMPTY
            );
        }
        boolean cond = entry.value() == null;
        ByteBuffer timestamp = ByteBuffer
                .allocate(Long.BYTES)
                .putLong(entry.timestamp());
        ByteBuffer value = ByteBuffer
                .allocate(cond ? 0 : (int) entry.value().byteSize());
        if (!cond) {
            value.put(entry.value().toByteArray());
        }
        return new Response(
                Response.OK,
                ByteBuffer
                        .allocate(timestamp.capacity() + value.capacity() + Integer.BYTES)
                        .put(timestamp.array())
                        .putInt(cond ? -1 : (int) entry.value().byteSize())
                        .put(value.array())
                        .array()
        );
    }

    private Response handlePut(Request request, @Param(value = "id") String id) {
        dao.upsert(new BaseEntry<>(
                fromString(id),
                MemorySegment.ofArray(request.getBody()),
                System.currentTimeMillis()
        ));
        return new Response(
                Response.CREATED,
                Response.EMPTY
        );
    }

    private Response handleDelete(@Param(value = "id") String id) {
        dao.upsert(new BaseEntry<>(
                fromString(id),
                null,
                System.currentTimeMillis()
        ));
        return new Response(
                Response.ACCEPTED,
                Response.EMPTY
        );
    }

    @Override
    public synchronized void stop() {
        for (SelectorThread selectorThread : selectors) {
            if (!selectorThread.isAlive()) {
                continue;
            }
            for (Session session : selectorThread.selector) {
                session.close();
            }
        }
        circuitBreaker.doShutdownNow();
        super.stop();
    }

    private void putNodesIllnessInfo(String node, boolean isIll) {
        circuitBreaker.putNodesIllnessInfo(node, isIll);
    }

    private HttpRequest.Builder request(String nodeUrl, String path) {
        return HttpRequest.newBuilder(URI.create(nodeUrl + path));
    }

    private HttpRequest.Builder requestForKey(String nodeUrl, String key) {
        return request(nodeUrl, "/v0/entity?id=" + key);
    }

    private HttpRequest buildHttpRequest(String key, String targetCluster, Request request)
            throws MethodNotAllowedException {
        if (request.getMethod() != Request.METHOD_GET && request.getMethod() != Request.METHOD_PUT
                && request.getMethod() != Request.METHOD_DELETE) {
            throw new MethodNotAllowedException();
        }

        HttpRequest.Builder httpRequest = requestForKey(targetCluster, key);
        int requestMethod = request.getMethod();
        if (requestMethod == Request.METHOD_GET) {
            httpRequest.GET();
        } else if (requestMethod == Request.METHOD_PUT) {
            httpRequest.PUT(HttpRequest.BodyPublishers.ofByteArray(request.getBody()));
        } else if (requestMethod == Request.METHOD_DELETE) {
            httpRequest.DELETE();
        }
        httpRequest.setHeader("internal", "true");
        return httpRequest.build();
    }

    private HttpResponse<byte[]> getResponseOrNull(CompletableFuture<HttpResponse<byte[]>> responseCompletableFuture)
            throws InterruptedException {
        try {
            return responseCompletableFuture.get(1, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            LOGGER.error("Error while working with response in {}", serviceConfig.selfUrl());
        }
        return null;
    }

    private List<String> getClustersByRendezvousHashing(String key, int ack) {
        Map<Integer, String> nodesHashes = new TreeMap<>();

        for (String nodeUrl : serviceConfig.clusterUrls()) {
            if (circuitBreaker.isNodeIll(nodeUrl)) {
                continue;
            }
            nodesHashes.put(Hash.murmur3(nodeUrl + key), nodeUrl);
        }
        return nodesHashes.values().stream().toList();
    }

    private MemorySegment fromString(String data) {
        return data == null ? null : MemorySegment.ofArray(data.getBytes(StandardCharsets.UTF_8));
    }
}
