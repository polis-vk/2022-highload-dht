package ok.dht.test.anikina;

import ok.dht.ServiceConfig;
import ok.dht.test.anikina.replication.ReplicationParameters;
import ok.dht.test.anikina.replication.SynchronizationHandler;
import ok.dht.test.anikina.utils.Utils;
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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static ok.dht.test.anikina.replication.SynchronizationHandler.SYNCHRONIZATION_PATH;

public class DatabaseHttpServer extends HttpServer {
    private static final Log log = LogFactory.getLog(DatabaseHttpServer.class);

    private static final String NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";
    private static final String QUERY_PATH = "/v0/entity";

    private static final int THREADS_MIN = 2;
    private static final int THREAD_MAX = 3;
    private static final int MAX_QUEUE_SIZE = 128;
    private static final int TERMINATION_TIMEOUT_MS = 800;

    private static final Set<Integer> SUPPORTED_METHODS = Set.of(
            Request.METHOD_GET,
            Request.METHOD_PUT,
            Request.METHOD_DELETE
    );

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
    private final ConsistentHashingImpl consistentHashing;
    private final DatabaseRequestHandler requestHandler;
    private final int numberOfNodes;
    private final SynchronizationHandler synchronizationHandler;

    public DatabaseHttpServer(ServiceConfig config) throws IOException {
        super(createHttpServerConfig(config.selfPort()));
        this.selfUrl = config.selfUrl();
        this.consistentHashing = new ConsistentHashingImpl(config.clusterUrls());
        this.requestHandler = new DatabaseRequestHandler(config.workingDir());
        this.numberOfNodes = config.clusterUrls().size();
        this.synchronizationHandler = new SynchronizationHandler();
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

                if (request.getPath().equals(SYNCHRONIZATION_PATH)) {
                    byte[] timestamp = Arrays.copyOfRange(request.getBody(), 0, Long.BYTES);
                    byte[] body = Arrays.copyOfRange(request.getBody(), Long.BYTES, request.getBody().length);
                    session.sendResponse(requestHandler.handle(request.getMethod(), key, body, timestamp));
                    return;
                }

                String from = request.getParameter("from=");
                String ack = request.getParameter("ack=");
                ReplicationParameters parameters = ReplicationParameters.parse(from, ack, this.numberOfNodes);

                if (!request.getPath().equals(QUERY_PATH)
                        || key == null
                        || key.isEmpty()
                        || parameters.getNumberOfAcks() == 0
                        || parameters.getNumberOfAcks() > parameters.getNumberOfReplicas()
                ) {
                    session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                    return;
                }
                if (!SUPPORTED_METHODS.contains(request.getMethod())) {
                    session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
                    return;
                }

                long timestamp = System.currentTimeMillis();
                Set<String> nodes = consistentHashing.getNodesByKey(key, parameters.getNumberOfReplicas());

                boolean saveToDao = nodes.remove(selfUrl);
                List<Response> responses = synchronizationHandler.forwardRequest(key, request, nodes, timestamp);
                if (saveToDao) {
                    Response selfResponse =
                            requestHandler.handle(
                                    request.getMethod(), key, request.getBody(), Utils.toByteArray(timestamp));
                    responses.add(selfResponse);
                }

                if (responses.size() < parameters.getNumberOfAcks()) {
                    session.sendResponse(new Response(NOT_ENOUGH_REPLICAS, Response.EMPTY));
                } else {
                    session.sendResponse(finalizeResponse(request.getMethod(), responses));
                }
            } catch (IOException e) {
                if (log.isDebugEnabled()) {
                    log.debug(e.getMessage());
                }
            }
        });
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
}
