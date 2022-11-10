package ok.dht.test.kuleshov;

import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.util.Hash;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static ok.dht.test.kuleshov.Validator.isCorrectAckFrom;
import static ok.dht.test.kuleshov.utils.RequestUtils.parseInt;
import static ok.dht.test.kuleshov.utils.ResponseUtils.emptyResponse;

public class CoolAsyncHttpServer extends CoolHttpServer {
    private static final int WORKER_CORE_POOL_SIZE = 4;
    private static final int WORKER_MAXIMUM_POOL_SIZE = 4;
    private static final int SENDER_CORE_POOL_SIZE = 4;
    private static final int SENDER_MAXIMUM_POOL_SIZE = 4;

    private final int defaultFrom;
    private final int defaultAck;
    private final String selfUrl;
    private final ExecutorService workerExecutorService;
    private final TreeSet<Integer> treeSet = new TreeSet<>();
    private final List<String> clusters;
    private final Map<Integer, Integer> hashToClusterIndex = new ConcurrentHashMap<>();
    private HttpClient httpClient;

    public CoolAsyncHttpServer(HttpServerConfig config, Service service, Object... routers) throws IOException {
        super(config, service, routers);
        workerExecutorService = new ThreadPoolExecutor(WORKER_CORE_POOL_SIZE,
                WORKER_MAXIMUM_POOL_SIZE,
                100,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(128)
        );

        selfUrl = service.getConfig().selfUrl();
        clusters = service.getConfig().clusterUrls();
        clusters.sort(Comparator.naturalOrder());

        int startRangeSize = Integer.MAX_VALUE / clusters.size();
        int cur = startRangeSize;
        defaultFrom = clusters.size();
        defaultAck = defaultFrom / 2 + 1;

        for (int i = 0; i < clusters.size(); i++) {
            treeSet.add(cur);
            hashToClusterIndex.put(cur, i);
            cur += startRangeSize;
        }
    }

    @Override
    public synchronized void start() {
        super.start();
        ExecutorService senderExecutorService = new ThreadPoolExecutor(SENDER_CORE_POOL_SIZE,
                SENDER_MAXIMUM_POOL_SIZE,
                100,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(128)
        );

        httpClient = HttpClient
                .newBuilder()
                .executor(senderExecutorService)
                .build();
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

                if (!path.equals("/v0/entity") && !path.equals("/master/v0/entity")) {
                    session.sendResponse(emptyResponse(Response.BAD_REQUEST));

                    return;
                }

                String id = request.getParameter("id=");
                if (id == null || id.isBlank()) {
                    session.sendResponse(emptyResponse(Response.BAD_REQUEST));

                    return;
                }

                boolean isSlave = path.startsWith("/master");

                if (isSlave) {
                    String str = request.getParameter("timestamp");
                    long time = -1;
                    if (str != null && !str.isBlank()) {
                        time = Long.parseLong(str);
                    }
                    Response resp = service.handle(method, id, request, time);
                    session.sendResponse(resp);

                    return;
                }

                Integer number = getVirtualNodeHash(id);

                if (number == null) {
                    session.sendResponse(emptyResponse(Response.NOT_FOUND));

                    return;
                }

                handleRequest(id, request, session);

            } catch (Exception e) {
                try {
                    session.sendResponse(emptyResponse(Response.BAD_REQUEST));
                } catch (IOException ex) {
                    session.close();
                    //ignore
                }
            }
        });
    }

    @Override
    public synchronized void stop() {
        terminateExecutor(workerExecutorService);

        super.stop();
    }

    private void handleRequest(String id, Request request, HttpSession session) throws IOException {
        Integer parseFrom = parseInt(request.getParameter("from="));
        Integer parseAck = parseInt(request.getParameter("ack="));

        int from = parseFrom == null ? defaultFrom : parseFrom;
        int ack = parseAck == null ? defaultAck : parseAck;

        if (!isCorrectAckFrom(ack, from, clusters.size())) {
            session.sendResponse(emptyResponse(Response.BAD_REQUEST));
            return;
        }

        AtomicInteger ackCount = new AtomicInteger(0);
        AtomicInteger allCount = new AtomicInteger(0);
        AtomicReference<MyResponse> lastResponse = new AtomicReference<>(null);
        long timestamp = System.currentTimeMillis();

        boolean isSelf = false;

        for (int reqIndex = 0, urlIndex = hashToClusterIndex.get(getVirtualNodeHash(id)); reqIndex < from; reqIndex++, urlIndex++) {
            urlIndex = urlIndex % clusters.size();

            if (clusters.get(urlIndex).equals(selfUrl)) {
                isSelf = true;
                continue;
            }

            HttpRequest req = HttpRequest.newBuilder()
                    .method(request.getMethodName(), HttpRequest.BodyPublishers.ofByteArray(request.getBody()))
                    .timeout(Duration.of(2, ChronoUnit.SECONDS))
                    .header("timestamp", String.valueOf(timestamp))
                    .uri(URI.create(clusters.get(urlIndex) + "/master" + request.getURI()))
                    .build();

            httpClient.sendAsync(req,
                    HttpResponse.BodyHandlers.ofByteArray()
            ).whenComplete((response, exception) -> {
                if (exception == null) {
                    handleResponse(request.getMethod(), MyResponse.fromHttpResponse(response), lastResponse, ack, ackCount, from, allCount, session);
                } else {
                    allCount.incrementAndGet();
                    int currentAll = allCount.incrementAndGet();

                    if (currentAll == from && ack < ackCount.get()) {
                        try {
                            session.sendResponse(new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY));
                        } catch (IOException e) {
                            session.close();
                            e.printStackTrace();
                        }
                    }
                }
            });
        }

        if (isSelf) {
            Response resp = service.handle(request.getMethod(), id, request, timestamp);
            handleResponse(request.getMethod(), MyResponse.fromOneResponse(resp), lastResponse, ack, ackCount, from, allCount, session);
        }
    }

    private static class MyResponse {
        private final byte[] body;
        private final int statusCode;
        private final long timestamp;

        public MyResponse(byte[] body, int statusCode, long timestamp) {
            this.body = Arrays.copyOf(body, body.length);
            this.statusCode = statusCode;
            this.timestamp = timestamp;
        }

        public static MyResponse fromHttpResponse(HttpResponse<byte[]> httpResponse) {
            Optional<String> timeStrOpt = httpResponse.headers().firstValue("timestamp");
            long time = -1;

            if (timeStrOpt.isPresent()) {
                time = Long.parseLong(timeStrOpt.get());
            }

            return new MyResponse(httpResponse.body(),
                    httpResponse.statusCode(),
                    time
            );
        }

        public static MyResponse fromOneResponse(Response response) {
            String timeStr = response.getHeader("timestamp");
            long time = -1;

            if (timeStr != null && !timeStr.isBlank()) {
                try {
                    time = Long.parseLong(timeStr);
                } catch (NumberFormatException ignored) {
                }
            }

            return new MyResponse(response.getBody(),
                    response.getStatus(),
                    time
            );
        }

        public byte[] getBody() {
            return body;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getStringStatusCode() {
            switch (statusCode) {
                case 200 -> {
                    return Response.OK;
                }
                case 201 -> {
                    return Response.CREATED;
                }
                case 202 -> {
                    return Response.ACCEPTED;
                }
                case 404 -> {
                    return Response.NOT_FOUND;
                }
                default -> {
                    return null;
                }
            }
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return "MyResponse{" +
                    ", body=" + body.length +
                    ", statusCode=" + statusCode +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }

    private void handleResponse(int method, MyResponse response, AtomicReference<MyResponse> lastResponse, int ack, AtomicInteger ackCount, int from, AtomicInteger allCount, HttpSession session) {
        switch (method) {
            case Request.METHOD_PUT -> {
                if (response.getStatusCode() == 201) {
                    int currentAck = ackCount.incrementAndGet();

                    if (currentAck == ack) {
                        try {
                            session.sendResponse(new Response(Response.CREATED, Response.EMPTY));
                            return;
                        } catch (IOException e) {
                            session.close();
                            e.printStackTrace();
                        }
                    }
                }
            }
            case Request.METHOD_DELETE -> {
                if (response.getStatusCode() == 202) {
                    int currentAck = ackCount.incrementAndGet();

                    if (currentAck == ack) {
                        try {
                            session.sendResponse(new Response(Response.ACCEPTED, Response.EMPTY));
                            return;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            case Request.METHOD_GET -> {
                if (response.getStatusCode() == 200 || response.getStatusCode() == 404) {
                    int currentAck = ackCount.incrementAndGet();

                    while (true) {
                        MyResponse currentLastResponse = lastResponse.get();
                        if (currentLastResponse == null || response.getTimestamp() > currentLastResponse.getTimestamp()) {
                            if (lastResponse.compareAndSet(currentLastResponse, response)) {
                                break;
                            } else {
                                continue;
                            }
                        }
                        break;
                    }

                    if (currentAck == ack) {
                        try {
                            MyResponse currentLatsResponse = lastResponse.get();
                            session.sendResponse(new Response(currentLatsResponse.getStringStatusCode(), currentLatsResponse.getBody()));
                            return;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        int currentAll = allCount.incrementAndGet();

        if (currentAll == from && ack < ackCount.get()) {
            try {
                session.sendResponse(new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Integer getVirtualNodeHash(String id) {
        int hash = Hash.murmur3(id);

        Integer next = treeSet.ceiling(hash);

        if (next != null) {
            return next;
        } else {
            return treeSet.ceiling(Integer.MIN_VALUE);
        }
    }

    private static void terminateExecutor(ExecutorService executorService) {
        boolean isFinished = false;
        try {
            isFinished = executorService.awaitTermination(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (isFinished) {
            executorService.shutdown();
        }

    }
}
