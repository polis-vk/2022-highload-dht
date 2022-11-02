package ok.dht.test.monakhov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.monakhov.hashing.JumpingNodesRouter;
import ok.dht.test.monakhov.hashing.NodesRouter;
import ok.dht.test.monakhov.repository.DaoRepository;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static ok.dht.test.monakhov.utils.ServiceUtils.createConfigFromPort;
import static ok.dht.test.monakhov.utils.ServiceUtils.isInvalidReplica;
import static ok.dht.test.monakhov.utils.ServiceUtils.responseBadRequest;
import static ok.dht.test.monakhov.utils.ServiceUtils.responseMethodNotAllowed;

public class DaoService implements Service {
    private static final Log log = LogFactory.getLog(DaoService.class);

    public static final String TIMESTAMP_HEADER = "TimeStamp";
    public static final int QUEUE_SIZE = 1000;
    private static final int CONNECTION_POOL_WORKERS = 32;
    private final ServiceConfig serviceConfig;
    private final NodesRouter nodesRouter;
    private DaoRepository dao;
    private HttpClient client;
    private AsyncHttpServer server;

    public DaoService(ServiceConfig serviceConfig) {
        this.serviceConfig = serviceConfig;
        nodesRouter = new JumpingNodesRouter(serviceConfig.clusterUrls());
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        server = new AsyncHttpServer(createConfigFromPort(serviceConfig));
        dao = new DaoRepository(serviceConfig.workingDir().toString());

        client = HttpClient.newBuilder().executor(new ThreadPoolExecutor(CONNECTION_POOL_WORKERS,
            CONNECTION_POOL_WORKERS, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(QUEUE_SIZE)
        )).build();

        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        if (server == null || dao == null || client == null) {
            return CompletableFuture.completedFuture(null);
        }
        server.stop();
        dao.close();

        server = null;
        client = null;
        dao = null;
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    public void manageRequest(
        @Param(value = "id") String id, @Param(value = "from") String fromParam,
        @Param(value = "ack") String ackParam, Request request, HttpSession session
    ) {
        try {
            if (id == null || id.isBlank() || isInvalidReplica(ackParam, fromParam)) {
                session.sendResponse(responseBadRequest());
                return;
            }

            if (request.getMethod() != Request.METHOD_GET && request.getMethod() != Request.METHOD_DELETE
                && request.getMethod() != Request.METHOD_PUT) {

                session.sendResponse(responseMethodNotAllowed());
                return;
            }

            int clusterSize = serviceConfig.clusterUrls().size();
            int ack = clusterSize / 2 + 1;
            int from = clusterSize;

            if (ackParam != null) {
                ack = Integer.parseInt(ackParam);
                from = Integer.parseInt(fromParam);

                if (from > serviceConfig.clusterUrls().size() || ack > from || ack <= 0) {
                    session.sendResponse(responseBadRequest());
                    return;
                }
            }

            String timestamp = request.getHeader(TIMESTAMP_HEADER + ":"); // wonderful one nio header parsing!!!!!!

            if (timestamp == null) {
                log.debug(String.format(
                    "Client's request received on node: %s. Method: %s. Session: %s",
                    serviceConfig.selfUrl(), request.getMethodName(), session
                ));

                String[] nodeUrls = nodesRouter.getNodeUrls(id, from);
                ReplicationHandler handler =
                    new ReplicationHandler(request, session, ack, from, serviceConfig.selfUrl(), id);
                multicast(request, id, nodeUrls, handler);
                return;
            }

            log.debug(String.format(
                "Redirected request received on node: %s. Method: %s. Session: %s",
                serviceConfig.selfUrl(), request.getMethodName(), session
            ));
            session.sendResponse(dao.executeDaoOperation(id, request, Timestamp.valueOf(timestamp)));
        } catch (Exception e) {
            log.error("Unexpected error while request handling occurred on node: " + serviceConfig.selfUrl(), e);
        }
    }

    private void multicast(Request request, String id, String[] nodeUrls, ReplicationHandler handler) {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        String selfUrl = serviceConfig.selfUrl();

        String stringTimestamp = timestamp.toString();
        byte[] body = request.getBody();
        var bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(body == null ? new byte[0] : body);

        for (String nodeUrl : nodeUrls) {
            if (nodeUrl.equals(selfUrl)) {
                CompletableFuture.runAsync(() -> {
                    Response response = dao.executeDaoOperation(id, request, timestamp);
                    handler.handleLocalResponse(response, nodeUrl);
                });
                continue;
            }

            HttpRequest redirectRequest =
                HttpRequest.newBuilder(URI.create(nodeUrl + request.getURI()))
                    .method(
                        request.getMethodName(),
                        bodyPublisher
                    )
                    .header(TIMESTAMP_HEADER, stringTimestamp)
                    .build();

            client.sendAsync(redirectRequest, HttpResponse.BodyHandlers.ofByteArray())
                .whenComplete((response, e) -> handler.handleRemoteResponse(response, e, nodeUrl));
        }
    }

    @ServiceFactory(stage = 5, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new DaoService(config);
        }
    }
}
