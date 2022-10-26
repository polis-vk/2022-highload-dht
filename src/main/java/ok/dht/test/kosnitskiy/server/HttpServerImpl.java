package ok.dht.test.kosnitskiy.server;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.kosnitskiy.dao.BaseEntry;
import ok.dht.test.kosnitskiy.dao.Entry;
import ok.dht.test.kosnitskiy.dao.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class HttpServerImpl extends HttpServer {
    private static final Logger LOG = LoggerFactory.getLogger(HttpServerImpl.class);
    private final ThreadPoolExecutor executor;
    private final MemorySegmentDao memorySegmentDao;
    private final HttpClient httpClient;

    private final String serverUrl;
    private final List<Cluster> clusters;

    private final HashFunction hash = Hashing.murmur3_128();

    private static final long SHUTDOWN_WAIT_TIME_SECONDS = 60;

    private static final int MAX_THREADS_PER_NODE = Runtime.getRuntime().availableProcessors() / 2 +
            (Runtime.getRuntime().availableProcessors() % 2  == 0 ? 0 : 1);
    private static final int MAX_TASKS_PER_NODE = MAX_THREADS_PER_NODE * 42;

    public HttpServerImpl(ServiceConfig config,
                          MemorySegmentDao memorySegmentDao,
                          ThreadPoolExecutor executor, Object... routers) throws IOException {
        super(createConfigFromPort(config.selfPort()), routers);
        this.executor = executor;
        this.memorySegmentDao = memorySegmentDao;
        this.httpClient = HttpClient.newHttpClient();
        clusters = config.clusterUrls().stream().map(Cluster::new).collect(Collectors.toList());
        serverUrl = config.selfUrl();

    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        String id = request.getParameter("id=");
        if (!isTypeSupported(request)) {
            session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
            return;
        }

        if (!"/v0/entity".equals(request.getPath()) || id == null || id.isEmpty()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        Cluster target = getTargetClusterFromKey(id);

        if (target.amountOfTasks.incrementAndGet() >= MAX_TASKS_PER_NODE) {
            target.amountOfTasks.decrementAndGet();
            session.sendResponse(new Response(Response.INTERNAL_ERROR, Response.EMPTY));
            return;
        }

        target.queue.add(() -> {
            try {
                if (target.url.equals(serverUrl)) {
                    handleSupported(request, session, id);
                } else {
                    HttpRequest proxyRequest = HttpRequest.newBuilder(URI.create(target.url + request.getURI()))
                            .method(
                                    request.getMethodName(),
                                    HttpRequest
                                            .BodyPublishers
                                            .ofByteArray(request.getBody() == null ? Response.EMPTY : request.getBody())
                            )
                            .timeout(Duration.ofMillis(100))
                            .build();
                    HttpResponse<byte[]> response = httpClient.send(proxyRequest,
                            HttpResponse.BodyHandlers.ofByteArray());

                    session.sendResponse(new Response(convertResponse(response.statusCode()), response.body()));
                }
            } catch (Exception e) {
                try {
                    session.sendResponse(new Response(
                            Response.INTERNAL_ERROR,
                            ("Internal server error has occurred: " + e.getMessage()).getBytes(StandardCharsets.UTF_8)
                    ));
                } catch (IOException ex) {
                    LOG.error("Unable to send answer to " + session.getRemoteHost());
                    session.scheduleClose();
                }
            }
        });

        if (target.amountOfWorkers.incrementAndGet() <= MAX_THREADS_PER_NODE) {
            executor.execute(() -> {
                Runnable task;
                while (true) {
                    task = target.queue.poll();
                    if (task == null) {
                        target.amountOfWorkers.decrementAndGet();
                        break;
                    }
                    LOG.debug("Url {} for id {} (my url is {})", target.url, id, serverUrl);
                    try {
                        task.run();
                    } catch (Exception e) {
                        LOG.error("Error while executing task");
                    } finally {
                        target.amountOfTasks.decrementAndGet();
                    }
                }
            });
        } else {
            target.amountOfWorkers.decrementAndGet();
        }
    }

    @Override
    public synchronized void stop() {
        executor.shutdown();
        boolean isTerminated;
        try {
            isTerminated = executor.awaitTermination(SHUTDOWN_WAIT_TIME_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            isTerminated = false;
            LOG.error("Waiting for tasks to finish timed out");
            Thread.currentThread().interrupt();
        }
        if (!isTerminated) {
            try {
                executor.shutdownNow();
            } catch (Exception exception) {
                LOG.error("Server won't shutdown");
                System.exit(1);
            }
        }
        for (SelectorThread selectorThread : selectors) {
            for (Session session : selectorThread.selector) {
                session.scheduleClose();
            }
        }
        super.stop();
    }

    private boolean isTypeSupported(Request request) {
        return request.getMethod() == Request.METHOD_GET
                || request.getMethod() == Request.METHOD_PUT
                || request.getMethod() == Request.METHOD_DELETE;
    }

    private void handleSupported(Request request, HttpSession session, String id) throws IOException {
        switch (request.getMethod()) {
            case Request.METHOD_GET -> {
                Entry<MemorySegment> entry;
                try {
                    entry = memorySegmentDao.get(MemorySegment.ofArray(id.toCharArray()));
                } catch (Exception e) {
                    LOG.error("Error occurred while getting " + id + ' ' + e.getMessage());
                    session.sendResponse(new Response(
                            Response.INTERNAL_ERROR,
                            e.getMessage().getBytes(StandardCharsets.UTF_8)
                    ));
                    return;
                }
                if (entry != null) {
                    session.sendResponse(new Response(
                            Response.OK,
                            entry.value().toByteArray()
                    ));
                    return;
                }
                session.sendResponse(new Response(Response.NOT_FOUND, Response.EMPTY));
            }
            case Request.METHOD_PUT -> {
                try {
                    memorySegmentDao.upsert(new BaseEntry<>(MemorySegment.ofArray(id.toCharArray()),
                            MemorySegment.ofArray(request.getBody())));
                } catch (Exception e) {
                    LOG.error("Error occurred while inserting " + id + ' ' + e.getMessage());
                    session.sendResponse(new Response(
                            Response.INTERNAL_ERROR,
                            e.getMessage().getBytes(StandardCharsets.UTF_8)
                    ));
                    return;
                }
                session.sendResponse(new Response(Response.CREATED, Response.EMPTY));
            }
            case Request.METHOD_DELETE -> {
                try {
                    memorySegmentDao.upsert(new BaseEntry<>(MemorySegment.ofArray(id.toCharArray()), null));
                } catch (Exception e) {
                    LOG.error("Error occurred while deleting " + id + ' ' + e.getMessage());
                    session.sendResponse(new Response(
                            Response.INTERNAL_ERROR,
                            e.getMessage().getBytes(StandardCharsets.UTF_8)
                    ));
                    return;
                }
                session.sendResponse(new Response(Response.ACCEPTED, Response.EMPTY));
            }
            default -> {
                session.sendResponse(new Response(
                        Response.INTERNAL_ERROR,
                        "Unsupported method was invoked somehow".getBytes(StandardCharsets.UTF_8)
                ));
            }
        }
    }

    private Cluster getTargetClusterFromKey(String key) {
        return clusters.get(
                hash.newHasher().putString(key, StandardCharsets.UTF_8).hash().hashCode() & 0x7FFFFFFF
                        % clusters.size());
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    private static String convertResponse(int statusCode) {
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
            default -> throw new IllegalArgumentException("Unknown status code: " + statusCode);
        };
    }

    private static class Cluster {
        public final String url;

        public final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<Runnable>();

        public final AtomicInteger amountOfTasks;
        public final AtomicInteger amountOfWorkers;

        public Cluster(String url) {
            this.url = url;
            this.amountOfTasks = new AtomicInteger(0);
            this.amountOfWorkers = new AtomicInteger(0);
        }
    }

}
