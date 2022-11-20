package ok.dht.test.shestakova;

import ok.dht.ServiceConfig;
import ok.dht.test.shestakova.dao.MemorySegmentDao;
import ok.dht.test.shestakova.exceptions.MethodNotAllowedException;
import ok.dht.test.shestakova.exceptions.NotEnoughReplicasException;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

public class DemoHttpServer extends HttpServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoHttpServer.class);
    private static final String RESPONSE_NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";
    private static final int NOT_FOUND_CODE = 404;
    private static final int METHOD_NOT_ALLOWED_CODE = 405;
    private final HttpClient httpClient;
    private final ServiceConfig serviceConfig;
    private final ExecutorService workersPool;
    private final CircuitBreakerImpl circuitBreaker;
    private final MemorySegmentDao dao;
    private final RequestsHandler requestsHandler;

    public DemoHttpServer(HttpServerConfig config, HttpClient httpClient, ExecutorService workersPool,
                          ServiceConfig serviceConfig, MemorySegmentDao dao, Object... routers) throws IOException {
        super(config, routers);
        this.httpClient = httpClient;
        this.serviceConfig = serviceConfig;
        this.workersPool = workersPool;
        this.circuitBreaker = new CircuitBreakerImpl(serviceConfig, httpClient);
        this.dao = dao;
        this.requestsHandler = new RequestsHandler(this.dao);
    }

    @Override
    public void handleRequest(Request request, HttpSession session) {
        String key = request.getParameter("id=");
        if (key == null || key.isEmpty()) {
            tryToSendResponseWithEmptyBody(session, Response.BAD_REQUEST);
            return;
        }

        int methodNum = request.getMethod();
        if (methodNum != Request.METHOD_GET && methodNum != Request.METHOD_PUT && methodNum != Request.METHOD_DELETE) {
            tryToSendResponseWithEmptyBody(session, Response.METHOD_NOT_ALLOWED);
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

        workersPool.execute(() -> {
            try {
                executeHandlingRequest(request, session, key, ack, from);
            } catch (MethodNotAllowedException e) {
                LOGGER.error("Method not allowed {} method: {}", serviceConfig.selfUrl(), request.getMethod());
                tryToSendResponseWithEmptyBody(session, Response.METHOD_NOT_ALLOWED);
            } catch (IOException e) {
                LOGGER.error("Internal error in server {}", serviceConfig.selfUrl());
                tryToSendResponseWithEmptyBody(session, Response.INTERNAL_ERROR);
            }
        });
    }

    private void executeHandlingRequest(Request request, HttpSession session, String key, int ack, int from)
            throws IOException {
        if (request.getHeader("internal") != null || request.getPath().contains("/service/message")) {
            Response response = handleInternalRequest(request, session);
            if (response != null) {
                session.sendResponse(response);
            }
            return;
        }

        List<String> targetNodes = HttpServerUtils.INSTANCE.getNodesSortedByRendezvousHashing(key, circuitBreaker,
                serviceConfig, from);
        List<HttpRequest> httpRequests = requestsHandler.getHttpRequests(request, key, targetNodes, serviceConfig);
        getResponses(request, session, ack, httpRequests)
                .whenComplete((list, throwable) -> {
                    if (throwable != null || list == null || list.size() < ack) {
                        tryToSendResponseWithEmptyBody(session, RESPONSE_NOT_ENOUGH_REPLICAS);
                        return;
                    }
                    try {
                        if (request.getMethod() != Request.METHOD_GET) {
                            session.sendResponse(list.get(0));
                            return;
                        }
                        aggregateResponsesAndSend(session, list);
                    } catch (IOException e) {
                        LOGGER.error("Internal error in server {}", serviceConfig.selfUrl());
                        tryToSendResponseWithEmptyBody(session, Response.INTERNAL_ERROR);
                    }
                });
    }

    private void aggregateResponsesAndSend(HttpSession session, List<Response> responses) throws IOException {
        byte[] body = null;
        int notFoundResponsesCount = 0;
        long maxTimestamp = Long.MIN_VALUE;
        for (Response response : responses) {
            if (response.getStatus() == NOT_FOUND_CODE) {
                notFoundResponsesCount++;
                continue;
            }
            ByteBuffer bodyBB = ByteBuffer.wrap(response.getBody());
            long timestamp = bodyBB.getLong();
            if (maxTimestamp < timestamp) {
                maxTimestamp = timestamp;
                body = HttpServerUtils.INSTANCE.getBody(bodyBB);
            }
        }

        boolean cond = body != null && notFoundResponsesCount != responses.size();
        session.sendResponse(new Response(
                cond ? Response.OK : Response.NOT_FOUND,
                cond ? body : Response.EMPTY
        ));
    }

    private CompletableFuture<List<Response>> getResponses(Request request, HttpSession session, int ack,
                                                           List<HttpRequest> httpRequests) {
        final CompletableFuture<List<Response>> resultFuture = new CompletableFuture<>();
        List<Response> responses = new CopyOnWriteArrayList<>();
        AtomicInteger successCount = new AtomicInteger(ack);
        // если ack < from, допускаем from - ack + 1 ошибку
        AtomicInteger errorCount = new AtomicInteger(httpRequests.size() - ack + 1);
        for (HttpRequest httpRequest : httpRequests) {
            if (httpRequest == null) {
                workersPool.execute(() -> {
                    Response response = handleInternalRequest(request, session);
                    if (response == null) {
                        if (errorCount.decrementAndGet() == 0) {
                            resultFuture.completeExceptionally(new NotEnoughReplicasException());
                        }
                        return;
                    }
                    responses.add(response);
                    if (successCount.decrementAndGet() == 0) {
                        resultFuture.complete(responses);
                    }
                });
                continue;
            }
            httpClient
                    .sendAsync(
                            httpRequest,
                            HttpResponse.BodyHandlers.ofByteArray()
                    )
                    .whenComplete((response, throwable) -> {
                        if (throwable != null) {
                            if (errorCount.decrementAndGet() == 0) {
                                resultFuture.completeExceptionally(new NotEnoughReplicasException());
                            }
                            return;
                        }
                        responses.add(new Response(
                                StatusCodes.statuses.getOrDefault(response.statusCode(), "UNKNOWN ERROR"),
                                response.body()
                        ));
                        if (successCount.decrementAndGet() == 0) {
                            resultFuture.complete(responses);
                        }
                    });
        }
        return resultFuture;
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
        Response response = null;
        String id = request.getParameter("id=");
        if (methodNum == Request.METHOD_GET) {
            response = requestsHandler.handleGet(id);
        } else if (methodNum == Request.METHOD_PUT) {
            String requestPath = request.getPath();
            if (requestPath.contains("/service/message")) {
                circuitBreaker.putNodesIllnessInfo(Arrays.toString(request.getBody()),
                        "/service/message/ill".equals(requestPath));
                tryToSendResponseWithEmptyBody(session, Response.SERVICE_UNAVAILABLE);
                return null;
            }
            response = requestsHandler.handlePut(request, id);
        } else if (methodNum == Request.METHOD_DELETE) {
            response = requestsHandler.handleDelete(id);
        }
        return response;
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
}
