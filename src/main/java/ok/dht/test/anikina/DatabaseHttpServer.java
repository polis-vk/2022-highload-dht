package ok.dht.test.anikina;

import ok.dht.ServiceConfig;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DatabaseHttpServer extends HttpServer {
    private static final Log log = LogFactory.getLog(DatabaseHttpServer.class);

    private static final int THREADS_MIN = 8;
    private static final int THREAD_MAX = 10;
    private static final int MAX_QUEUE_SIZE = 128;
    private static final int TERMINATION_TIMEOUT_MS = 800;

    private final ExecutorService executorService =
            new ThreadPoolExecutor(
                    THREADS_MIN,
                    THREAD_MAX,
                    0,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(MAX_QUEUE_SIZE),
                    new ThreadPoolExecutor.AbortPolicy()
            );
    private final String selfUrl;
    private final HttpClient client;
    private final ConsistentHashingImpl consistentHashing;
    private final DatabaseRequestHandler requestHandler;

    public DatabaseHttpServer(ServiceConfig config) throws IOException {
        super(createHttpServerConfig(config.selfPort()));
        this.client = HttpClient.newHttpClient();
        this.selfUrl = config.selfUrl();
        this.consistentHashing = new ConsistentHashingImpl(config.clusterUrls());
        this.requestHandler = new DatabaseRequestHandler(config.workingDir());
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
    public void handleRequest(Request request, final HttpSession session) {
        executorService.execute(() -> {
            try {
                String key = request.getParameter("id=");
                if (!request.getPath().equals("/v0/entity") || key == null || key.isEmpty()) {
                    session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                    return;
                }

                String shard = consistentHashing.getShardByKey(key);
                Response response;
                if (shard.equals(selfUrl)) {
                    response = requestHandler.handle(key, request);
                } else {
                    response = proxyRequest(shard, request);
                }
                session.sendResponse(response);
            } catch (IOException e) {
                if (log.isDebugEnabled()) {
                    log.debug(e.getMessage());
                }
            }
        });
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

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(TERMINATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }

        requestHandler.close();
    }

    private Response proxyRequest(String serverUrl, Request request) {
        HttpRequest httpRequest =
                HttpRequest.newBuilder(URI.create(serverUrl + request.getURI()))
                        .method(
                                request.getMethodName(),
                                HttpRequest.BodyPublishers.ofByteArray(request.getBody()))
                        .build();
        try {
            HttpResponse<byte[]> httpResponse =
                    client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            String status = switch (httpResponse.statusCode()) {
                case HttpURLConnection.HTTP_OK -> Response.OK;
                case HttpURLConnection.HTTP_CREATED -> Response.CREATED;
                case HttpURLConnection.HTTP_ACCEPTED -> Response.ACCEPTED;
                case HttpURLConnection.HTTP_BAD_REQUEST -> Response.BAD_REQUEST;
                case HttpURLConnection.HTTP_NOT_FOUND -> Response.NOT_FOUND;
                case HttpURLConnection.HTTP_BAD_METHOD -> Response.METHOD_NOT_ALLOWED;
                case HttpURLConnection.HTTP_INTERNAL_ERROR -> Response.INTERNAL_ERROR;
                default -> throw new IllegalStateException("Unexpected status code: " + httpResponse.statusCode());
            };
            return new Response(status, httpResponse.body());
        } catch (HttpConnectTimeoutException e) {
            return new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Error while proxying request happened", e);
            }
            return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
        }
    }
}
