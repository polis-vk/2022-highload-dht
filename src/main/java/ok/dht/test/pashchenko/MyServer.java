package ok.dht.test.pashchenko;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.pashchenko.dao.Config;
import ok.dht.test.pashchenko.dao.Entry;
import ok.dht.test.pashchenko.dao.MemorySegmentDao;
import one.nio.http.*;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import one.nio.util.Utf8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MyServer extends HttpServer {
    private static final Logger LOG = LoggerFactory.getLogger(MyServer.class);
    private static final String REMOTE_HEADER_TIMESTAMP = "remote-ts";

    private final MemorySegmentDao dao;
    private final Executor executor;
    private final Executor executorAggregator;
    private final HttpClient client;
    private final ServiceConfig config;
    private final List<Node> nodes;

    public MyServer(ServiceConfig config) throws IOException {
        super(createConfigFromPort(config.selfPort()));
        this.config = config;
        dao = new MemorySegmentDao(new Config(config.workingDir(), 1048576L));
        executor = Executors.newFixedThreadPool(16);
        executorAggregator = Executors.newFixedThreadPool(16);
        client = HttpClient.newHttpClient();

        nodes = new ArrayList<>(config.clusterUrls().size());
        for (String url : config.clusterUrls()) {
            nodes.add(new Node(url));
        }
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (!"/v0/entity".equals(request.getPath())) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        String id = request.getParameter("id=");
        if (id == null || id.isEmpty()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        int from;
        int ack;
        try {
            from = getInt(request, "from=", nodes.size());
            ack = getInt(request, "ack=", (nodes.size() / 2) + 1);
            if (ack > from || ack <= 0) {
                session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                return;
            }
        } catch (Exception_400 e) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }
        String remoteHeaderTimestamp = request.getHeader(REMOTE_HEADER_TIMESTAMP + ":");


        int firstIndex = getNodeIndexForKey(id);

        Response[] responses = new Response[from];
        CountDownLatch countDownLatch = new CountDownLatch(from);
        for (int i = 0; i < from; i++) {
            int iFinal = i;
            Node node = nodes.get((firstIndex + i) % nodes.size());

            int tasks = node.tasksCount.incrementAndGet();
            if (tasks > Node.MAX_TASKS_ALLOWED) {
                node.tasksCount.decrementAndGet();
                session.sendResponse(new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY));
                return;
            }


            node.tasks.add(() -> {
                try {
                    String url = node.url;
                    LOG.debug("Url {} for id {} (my port is {})", url, id, config.selfPort());
                    responses[iFinal] =
                            url.equals(config.selfUrl())
                                    ? handleRequest(request, id)
                                    : proxyRequest(request, url);
                    countDownLatch.countDown();
                } catch (Exception e) {
                    LOG.error("error handle request", e);
                    sendError(session);
                    responses[iFinal] = new Response(Response.INTERNAL_ERROR, Response.EMPTY);
                    countDownLatch.countDown();
                }
            });


            if (node.maxWorkersSemaphore.tryAcquire()) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        Runnable poll = node.tasks.poll();

                        if (poll == null) {
                            node.maxWorkersSemaphore.release();
                            return;
                        }

                        try {
                            poll.run();
                        } catch (Exception e) {
                            LOG.error("Unexpected error handle request", e);
                        } finally {
                            node.tasksCount.decrementAndGet();
                            executor.execute(this);
                        }
                    }
                });
            }
        }

        executorAggregator.execute(() -> {
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                LOG.error("interrupted executor aggregator", e);
                sendError(session);
            }

            for (Response response : responses) {
                //
            }
        });
    }

    private int getInt(Request request, String param, int defaultValue) throws Exception_400 {
        String fromStr = request.getParameter(param);
        if (fromStr == null) {
            return defaultValue;
        } else {
            try {
                return Integer.parseInt(fromStr);
            } catch (NumberFormatException e) {
                LOG.error("Error parse int" + param, e);
                throw new Exception_400();
            }
        }
    }

    private int getNodeIndexForKey(String id) {
        int hash = Integer.MAX_VALUE;
        int maxIndex = 0;
        int idHash = id.hashCode() * 17;
        int i = 0;
        for (Node node : nodes) {
            int newHash = idHash + node.url.hashCode();
            if (newHash < hash) {
                hash = newHash;
                maxIndex = i;
            }
            i++;
        }
        return maxIndex;
    }

    private static void sendError(HttpSession session) {
        try {
            session.sendResponse(new Response(Response.INTERNAL_ERROR, Response.EMPTY));
        } catch (Exception ex) {
            LOG.error("error send response", ex);
            sessionClose(session);
        }
    }

    private static void sessionClose(HttpSession session) {
        try {
            session.close();
        } catch (Exception e) {
            LOG.error("error close session", e);
        }
    }

    private Response proxyRequest(Request request, String url) throws IOException, InterruptedException {
        HttpRequest proxyRequest = HttpRequest.newBuilder(URI.create(url + request.getURI()))
                .method(
                        request.getMethodName(),
                        request.getBody() == null
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofByteArray(request.getBody())
                ).build();

        HttpResponse<byte[]> response = client.send(proxyRequest, HttpResponse.BodyHandlers.ofByteArray());
        String status = switch (response.statusCode()) {
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
            default -> throw new IllegalArgumentException("Unknown status code: " + response.statusCode());
        };
        return new Response(status, response.body());
    }

    private Response handleRequest(Request request, String id) {
        switch (request.getMethod()) {
            case Request.METHOD_GET -> {
                Entry entry = dao.get(MemorySegment.ofArray(Utf8.toBytes(id)));
                if (entry == null) {
                    return new Response(Response.NOT_FOUND, Response.EMPTY);
                }
                return new Response(Response.OK, entry.value().toByteArray());
            }
            case Request.METHOD_PUT -> {
                dao.upsert(new Entry(MemorySegment.ofArray(Utf8.toBytes(id)), MemorySegment.ofArray(request.getBody())));
                return new Response(Response.CREATED, Response.EMPTY);
            }
            case Request.METHOD_DELETE -> {
                dao.upsert(new Entry(MemorySegment.ofArray(Utf8.toBytes(id)), null));
                return new Response(Response.ACCEPTED, Response.EMPTY);
            }
            default -> {
                return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            }
        }
    }

    @Override
    public synchronized void stop() {
        super.stop();
        for (SelectorThread selector : selectors) {
            for (Session session : selector.selector) {
                session.close();
            }
        }
        try {
            dao.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static class Node {
        static final int MAX_TASKS_ALLOWED = 128;
        static final int MAX_WORKERS_ALLOWED = 1;

        final String url;
        final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();

        final AtomicInteger tasksCount = new AtomicInteger(0);
        final Semaphore maxWorkersSemaphore = new Semaphore(MAX_WORKERS_ALLOWED);

        Node(String url) {
            this.url = url;
        }
    }

}
