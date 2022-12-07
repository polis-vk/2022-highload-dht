package ok.dht.test.kuleshov;

import ok.dht.test.kuleshov.sharding.ConsistentHashingManager;
import ok.dht.test.kuleshov.sharding.HashRange;
import ok.dht.test.kuleshov.sharding.Shard;
import ok.dht.test.kuleshov.utils.RequestsUtils;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Socket;
import one.nio.server.RejectedSessionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static ok.dht.test.kuleshov.Validator.isCorrectAckFrom;
import static ok.dht.test.kuleshov.utils.RequestsUtils.getTimestampHeader;
import static ok.dht.test.kuleshov.utils.ResponseUtils.emptyResponse;

public class CoolAsyncHttpServer extends CoolHttpServer {
    private static final String TIMESTAMP_HEADER = "timestamp";
    private static final String ERROR_RESPONSE_SENDING = "Error sending response to client: ";
    private static final String ERROR_SENDING_ERROR = "Error sending error to client: ";

    private static final int WORKER_CORE_POOL_SIZE = 4;
    private static final int WORKER_MAXIMUM_POOL_SIZE = 4;
    private static final int SENDER_CORE_POOL_SIZE = 4;
    private static final int SENDER_MAXIMUM_POOL_SIZE = 4;

    private final int defaultFrom;
    private final int defaultAck;
    private final String selfUrl;
    private ExecutorService workerExecutorService;
    private final ConsistentHashingManager consistentHashingManager;
    private final TransferService transferService = new TransferService();
    private final List<String> clusters;
    private HttpClient httpClient = HttpClient.newHttpClient();
    private final Logger log = LoggerFactory.getLogger(CoolAsyncHttpServer.class);

    public CoolAsyncHttpServer(HttpServerConfig config, boolean isAddedNode, Service service, Object... routers) throws IOException {
        super(config, service, routers);

        selfUrl = service.getConfig().selfUrl();
        clusters = service.getConfig().clusterUrls();
        clusters.sort(Comparator.naturalOrder());
        consistentHashingManager = new ConsistentHashingManager();
        for (String shard : clusters) {
            if (isAddedNode && shard.equals(selfUrl)) {
                continue;
            }
            consistentHashingManager.addNode(shard);
        }

        if (isAddedNode) {
            consistentHashingManager.addNode(selfUrl);
        }

        for (String shard : clusters) {
            if (isAddedNode && shard.equals(selfUrl)) {
                continue;
            }
            consistentHashingManager.addNode(shard);
        }

        defaultFrom = clusters.size();
        defaultAck = defaultFrom / 2 + 1;

        if (isAddedNode) {
            for (String shard : clusters) {
                if (!Objects.equals(shard, selfUrl)) {
                    sendAddNode(shard);
                }
            }
        }
    }

    private void sendAddNode(String url) {
        HttpRequest request = HttpRequest.newBuilder().PUT(HttpRequest.BodyPublishers.ofString(selfUrl)).uri(URI.create(url + "/addnode")).timeout(Duration.ofSeconds(2)).build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("error sending add node url: " + url + ", error: " + e);
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("error response add node url: " + url + ", error: " + response.statusCode());
        }
    }

    @Override
    public synchronized void start() {
        workerExecutorService = new ThreadPoolExecutor(WORKER_CORE_POOL_SIZE,
                WORKER_MAXIMUM_POOL_SIZE,
                100,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(128)
        );

        ExecutorService senderExecutorService = new ThreadPoolExecutor(SENDER_CORE_POOL_SIZE,
                SENDER_MAXIMUM_POOL_SIZE,
                100,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>()
        );

        httpClient = HttpClient
                .newBuilder()
                .executor(senderExecutorService)
                .build();

        super.start();
    }

    private void handleAddShardRequest(Request request, HttpSession session) throws IOException {
        String url = new String(request.getBody(), StandardCharsets.UTF_8);

        Map<Shard, Set<HashRange>> map = consistentHashingManager.addNode(url);
        Set<HashRange> hashRangeSet = map.get(new Shard(selfUrl));

        clusters.add(url);
        clusters.sort(Comparator.naturalOrder());

        session.sendResponse(new Response(Response.OK, Response.EMPTY));

        transferService.transfer(new Shard(url), hashRangeSet, service.getAll());
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        workerExecutorService.execute(() -> {
            try {
                int method = request.getMethod();
                if (!SUPPORTED_METHODS.contains(method)) {
                    session.sendResponse(emptyResponse(Response.METHOD_NOT_ALLOWED));

                    return;
                }

                String path = request.getPath();
                if ("/addnode".equals(path)) {
                    System.out.println(new String(request.getBody(), StandardCharsets.UTF_8));
                    handleAddShardRequest(request, session);
                    return;
                }

                if ("/v0/entities".equals(path)
                ) {
                    handleRangeRequest(request, session);

                    return;
                }

                String id = request.getParameter("id=");
                if (id == null || id.isBlank()) {
                    session.sendResponse(emptyResponse(Response.BAD_REQUEST));

                    return;
                }

                System.out.println(request.getMethodName() + " id=" + id + ", shard=" + consistentHashingManager.getShardByKey(id).getUrl());

                if (transferService.isInTransfer(id)) {
                    session.sendResponse(emptyResponse(Response.GATEWAY_TIMEOUT));

                    return;
                }

                switch (path) {
                    case "/v0/entity" -> handleRequest(id, request, session);
                    case "/master/v0/entity" -> {
                        Response resp = service.handle(method, id, request, getTimestampHeader(request));
                        session.sendResponse(resp);
                    }
                    default -> session.sendResponse(emptyResponse(Response.BAD_REQUEST));
                }
            } catch (Exception e) {
                log.error(ERROR_RESPONSE_SENDING + e);
                try {
                    session.sendResponse(emptyResponse(Response.BAD_REQUEST));
                } catch (IOException exception) {
                    log.error(ERROR_SENDING_ERROR + exception.getMessage());
                    session.close();
                }
            }
        });
    }

    @Override
    public synchronized void stop() {
        terminateExecutor(workerExecutorService);

        super.stop();
    }

    private void handleRangeRequest(Request request, HttpSession session) {
        String start = request.getParameter("start=");
        String end = request.getParameter("end=");
        if (start == null || start.isBlank() || (end != null && end.isBlank())) {
            try {
                session.sendResponse(emptyResponse(Response.BAD_REQUEST));
            } catch (IOException e) {
                log.error(ERROR_RESPONSE_SENDING + e.getCause());
                try {
                    session.sendResponse(emptyResponse(Response.SERVICE_UNAVAILABLE));
                } catch (IOException exception) {
                    log.error(ERROR_SENDING_ERROR + exception.getMessage());
                    session.close();
                }
            }

            return;
        }

        try {
            Iterator<byte[]> chunkIterator = new ChunkIterator(service.getRange(start, end));

            session.sendResponse(createChunkedHeaderResponse());
            ((CoolSession) session).writeChunks(chunkIterator);
        } catch (IOException e) {
            log.error(ERROR_RESPONSE_SENDING + e.getCause());
            try {
                session.sendResponse(emptyResponse(Response.BAD_REQUEST));
            } catch (IOException exception) {
                log.error(ERROR_SENDING_ERROR + exception.getMessage());
                session.close();
            }
        }

    }

    public static Response createChunkedHeaderResponse() {
        Response response = new Response(Response.OK);
        response.addHeader("Transfer-Encoding: chunked");

        return response;
    }

    @Override
    public HttpSession createSession(Socket socket) throws RejectedSessionException {
        return new CoolSession(socket, this);
    }

    private void handleRequest(String id, Request request, HttpSession session) {
        Integer parseFrom = RequestsUtils.parseInt(request.getParameter("from="));
        Integer parseAck = RequestsUtils.parseInt(request.getParameter("ack="));

        int from = parseFrom == null ? defaultFrom : parseFrom;
        int ack = parseAck == null ? defaultAck : parseAck;

        if (!isCorrectAckFrom(ack, from, clusters.size())) {
            try {
                session.sendResponse(emptyResponse(Response.BAD_REQUEST));
            } catch (IOException exception) {
                log.error(exception.getMessage());
                session.close();
            }
            return;
        }

        SlaveResponseHandler slaveResponseHandler = new SlaveResponseHandler(ack, from, session);
        long timestamp = System.currentTimeMillis();

        boolean isSelf = false;

        Shard shard = consistentHashingManager.getShardByKey(id);

        System.out.println(shard.getUrl());
        final int startIndex = clusters.indexOf(shard.getUrl());

        for (int reqIndex = 0; reqIndex < from; reqIndex++) {
            String slaveUrl = clusters.get((startIndex + reqIndex) % clusters.size());

            if (slaveUrl.equals(selfUrl)) {
                isSelf = true;
                continue;
            }

            HttpRequest requestToSlave = createRequestToSlave(request, slaveUrl, timestamp);
            httpClient.sendAsync(requestToSlave,
                    HttpResponse.BodyHandlers.ofByteArray()
            ).whenComplete((response, exception) -> {
                if (exception == null) {
                    slaveResponseHandler.handleResponse(request.getMethod(), HandleResponse.fromHttpResponse(response));
                } else {
                    log.error("Error slave's response" + exception.getMessage());
                    slaveResponseHandler.handleFrom();
                }
            });
        }

        if (isSelf) {
            Response selfResponse = service.handle(request.getMethod(), id, request, timestamp);
            slaveResponseHandler.handleResponse(request.getMethod(), HandleResponse.fromOneResponse(selfResponse));
        }
    }

    private HttpRequest createRequestToSlave(Request request, String url, long timestamp) {
        return HttpRequest.newBuilder()
                .method(request.getMethodName(), HttpRequest.BodyPublishers.ofByteArray(
                        request.getBody() == null ? new byte[0] : request.getBody()
                ))
                .timeout(Duration.of(2, ChronoUnit.SECONDS))
                .header(TIMESTAMP_HEADER, String.valueOf(timestamp))
                .uri(URI.create(url + "/master" + request.getURI()))
                .build();
    }

    private static void terminateExecutor(ExecutorService executorService) {
        boolean isFinished = false;
        executorService.shutdown();

        try {
            isFinished = executorService.awaitTermination(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (isFinished) {
            executorService.shutdownNow();
        }

    }
}
