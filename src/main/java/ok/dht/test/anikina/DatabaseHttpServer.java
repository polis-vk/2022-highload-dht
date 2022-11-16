package ok.dht.test.anikina;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.anikina.consistenthashing.ConsistentHashingImpl;
import ok.dht.test.anikina.dao.Entry;
import ok.dht.test.anikina.replication.ReplicationParameters;
import ok.dht.test.anikina.streaming.ChunkedResponse;
import ok.dht.test.anikina.streaming.StreamingHttpSession;
import ok.dht.test.anikina.utils.RequestUtils;
import ok.dht.test.anikina.utils.Utils;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.server.AcceptorConfig;
import one.nio.server.RejectedSessionException;
import one.nio.server.SelectorThread;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static ok.dht.test.anikina.utils.RequestUtils.SYNCHRONIZATION_PATH;

public class DatabaseHttpServer extends HttpServer {
    private static final Log log = LogFactory.getLog(DatabaseHttpServer.class);

    private static final String QUERY_PATH = "/v0/entity";
    private static final String STREAMING_PATH = "/v0/entities";

    private static final String NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";

    private static final int HTTP_SERVICE_THREADS = 3;
    private static final int DAO_SERVICE_THREADS = 3;
    private static final int MAX_QUEUE_SIZE = 128;
    private static final int TERMINATION_TIMEOUT_MS = 800;

    private static final Set<Integer> SUPPORTED_METHODS = Set.of(
            Request.METHOD_GET,
            Request.METHOD_PUT,
            Request.METHOD_DELETE
    );

    private final ExecutorService httpRequestService =
            new ThreadPoolExecutor(
                    HTTP_SERVICE_THREADS,
                    HTTP_SERVICE_THREADS,
                    0,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(MAX_QUEUE_SIZE),
                    new ThreadPoolExecutor.AbortPolicy()
            );
    private final ExecutorService daoRequestService =
            Executors.newFixedThreadPool(DAO_SERVICE_THREADS);

    private final String selfUrl;
    private final ConsistentHashingImpl consistentHashing;
    private final DatabaseRequestHandler requestHandler;
    private final int numberOfNodes;
    private final HttpClient client;

    public DatabaseHttpServer(ServiceConfig config) throws IOException {
        super(createHttpServerConfig(config.selfPort()));
        this.selfUrl = config.selfUrl();
        this.consistentHashing = new ConsistentHashingImpl(config.clusterUrls());
        this.requestHandler = new DatabaseRequestHandler(config.workingDir());
        this.numberOfNodes = config.clusterUrls().size();
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    private static HttpServerConfig createHttpServerConfig(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptorConfig = new AcceptorConfig();
        acceptorConfig.port = port;
        acceptorConfig.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptorConfig};
        return httpConfig;
    }

    @Override
    public HttpSession createSession(Socket socket) throws RejectedSessionException {
        return new StreamingHttpSession(socket, this);
    }

    @Override
    public void handleRequest(Request request, final HttpSession session) {
        httpRequestService.execute(() -> {
            try {
                if (!SUPPORTED_METHODS.contains(request.getMethod())) {
                    session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
                    return;
                }

                switch (request.getPath()) {
                    case SYNCHRONIZATION_PATH ->
                            processSynchronizationRequest(request, session);
                    case QUERY_PATH ->
                            processQueryRequest(request, session);
                    case STREAMING_PATH -> processStreamingRequest(request, session);
                    default ->
                            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                }
            } catch (IOException e) {
                if (log.isDebugEnabled()) {
                    log.debug(e.getMessage());
                }
            }
        });
    }

    private void processStreamingRequest(Request request, final HttpSession session) throws IOException {
        StreamingHttpSession streamingSession = (StreamingHttpSession) session;
        String start = request.getParameter("start=");
        String end = request.getParameter("end=");

        if (invalidKey(end)) {
            end = null;
        }
        if (invalidKey(start) || (end != null && start.compareTo(end) >= 0)) {
            streamingSession.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        Iterator<Entry<MemorySegment>> iterator = requestHandler.getIterator(start, end);

        streamingSession.sendResponse(iterator);
    }

    private void processSynchronizationRequest(Request request, final HttpSession session)
            throws IOException {
        String key = request.getParameter("id=");

        byte[] timestamp = Arrays.copyOfRange(request.getBody(), 0, Long.BYTES);
        byte[] body = Arrays.copyOfRange(request.getBody(), Long.BYTES, request.getBody().length);
        session.sendResponse(requestHandler.handle(request.getMethod(), key, body, timestamp));
    }

    private void processQueryRequest(Request request, final HttpSession session)
            throws IOException {
        String key = request.getParameter("id=");

        ReplicationParameters parameters =
                ReplicationParameters.parse(
                        request.getParameter("from="),
                        request.getParameter("ack="),
                        this.numberOfNodes
                );

        if (invalidKey(key) || parameters.areInvalid()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        session.sendResponse(aggregateResponse(parameters, key, request));
    }

    private Response aggregateResponse(
            ReplicationParameters parameters, String key, Request request) {
        long timestamp = System.currentTimeMillis();
        Set<String> nodes = consistentHashing.getNodesByKey(key, parameters.getNumberOfReplicas());

        boolean saveToDao = nodes.remove(selfUrl);

        ConcurrentLinkedQueue<Response> validResponses = new ConcurrentLinkedQueue<>();
        AtomicInteger failCount = new AtomicInteger();

        for (String node : nodes) {
            HttpRequest httpRequest = RequestUtils.makeHttpRequest(node, key, request, timestamp);
            CompletableFuture<?> ignored = client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray())
                    .thenApply(httpResponse ->
                            new Response(
                                    RequestUtils.matchStatusCode(httpResponse.statusCode()),
                                    httpResponse.body()
                            )
                    ).whenComplete((response, throwable) -> {
                        if (throwable == null) {
                            validResponses.add(response);
                        } else {
                            failCount.incrementAndGet();
                        }
                    });
        }
        if (saveToDao) {
            CompletableFuture<?> ignored = CompletableFuture.supplyAsync(() ->
                    requestHandler.handle(
                            request.getMethod(),
                            key,
                            request.getBody(),
                            Utils.toByteArray(timestamp)
                    ),
                    daoRequestService
            ).whenComplete((response, throwable) -> validResponses.add(response));
        }

        while (true) {
            int currFailCount = failCount.get();
            if (currFailCount > parameters.getNumberOfReplicas() - parameters.getNumberOfAcks()) {
                return new Response(NOT_ENOUGH_REPLICAS, Response.EMPTY);
            }
            if (validResponses.size() >= parameters.getNumberOfAcks()) {
                return finalizeResponse(request.getMethod(), List.copyOf(validResponses));
            }
        }
    }

    private Response finalizeResponse(int method, List<Response> responses) {
        if (method != Request.METHOD_GET) {
            return responses.get(0);
        }

        long maxTombstoneTimestamp = -1;
        long maxValueTimestamp = -1;
        byte[] value = new byte[0];

        for (Response response : responses) {
            byte[] body = response.getBody();
            if (response.getStatus() == HttpURLConnection.HTTP_NOT_FOUND
                    && body.length > 0) {
                long timestamp = Utils.longFromByteArray(body);
                maxTombstoneTimestamp = Math.max(maxTombstoneTimestamp, timestamp);
            }
            if (response.getStatus() == HttpURLConnection.HTTP_OK) {
                long timestamp =
                        Utils.longFromByteArray(
                                Arrays.copyOfRange(body, 0, Long.BYTES));
                if (timestamp > maxValueTimestamp) {
                    maxValueTimestamp = timestamp;
                    value = Arrays.copyOfRange(body, Long.BYTES, body.length);
                }
            }
        }
        if (maxValueTimestamp == -1 || maxTombstoneTimestamp > maxValueTimestamp) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        } else {
            return new Response(Response.OK, value);
        }
    }

    private boolean invalidKey(String key) {
        return key == null || key.isEmpty();
    }

    @Override
    public synchronized void stop() {
        for (SelectorThread selectorThread : selectors) {
            if (selectorThread.selector.isOpen()) {
                for (Session session : selectorThread.selector) {
                    session.close();
                }
            }
        }
        super.stop();
    }

    public void close() throws IOException {
        stop();

        httpRequestService.shutdown();
        try {
            if (!httpRequestService.awaitTermination(TERMINATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                httpRequestService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            httpRequestService.shutdownNow();
        }

        requestHandler.close();
    }
}
