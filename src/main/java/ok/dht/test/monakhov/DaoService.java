package ok.dht.test.monakhov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.monakhov.hashing.JumpingNodesRouter;
import ok.dht.test.monakhov.hashing.NodesRouter;
import ok.dht.test.monakhov.model.EntryWrapper;
import ok.dht.test.monakhov.repository.DaoRepository;
import ok.dht.test.monakhov.utils.ExecutorUtils;
import one.nio.http.HttpException;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.pool.PoolException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.net.HttpURLConnection.HTTP_ACCEPTED;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static ok.dht.test.monakhov.utils.ServiceUtils.QUEUE_SIZE;
import static ok.dht.test.monakhov.utils.ServiceUtils.TIMESTAMP_HEADER;
import static ok.dht.test.monakhov.utils.ServiceUtils.createConfigFromPort;
import static ok.dht.test.monakhov.utils.ServiceUtils.isInvalidReplica;
import static ok.dht.test.monakhov.utils.ServiceUtils.responseAccepted;
import static ok.dht.test.monakhov.utils.ServiceUtils.responseBadRequest;
import static ok.dht.test.monakhov.utils.ServiceUtils.responseCreated;
import static ok.dht.test.monakhov.utils.ServiceUtils.responseMethodNotAllowed;
import static ok.dht.test.monakhov.utils.ServiceUtils.responseNotEnoughReplicas;
import static ok.dht.test.monakhov.utils.ServiceUtils.responseNotFound;
import static ok.dht.test.monakhov.utils.ServiceUtils.responseOk;
import static one.nio.serial.Serializer.deserialize;

public class DaoService implements Service {
    private static final Log log = LogFactory.getLog(DaoService.class);

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

        client = HttpClient.newBuilder().executor(new ThreadPoolExecutor(CONNECTION_POOL_WORKERS, CONNECTION_POOL_WORKERS,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(QUEUE_SIZE)
        )).build();

        // client = HttpClient.newBuilder().build();

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
        dao = null;
        client = null;
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
                && request.getMethod() != Request.METHOD_PUT)
            {
                session.sendResponse(responseMethodNotAllowed());
                return;
            }

            int clusterSize = serviceConfig.clusterUrls().size();
            int ack = clusterSize;
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
                multicast(request, id, nodeUrls, ack, from, session);
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

    private void multicast(Request request, String id, String[] nodeUrls, int ack, int from, HttpSession session) {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        AtomicInteger atomicFinished = new AtomicInteger(0);
        AtomicInteger atomicResponded = new AtomicInteger(0);
        AtomicInteger atomicAcked = new AtomicInteger(0);
        AtomicReference<EntryWrapper> atomicLastEntry = new AtomicReference<>();
        byte[] body = request.getBody();
        final var bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(body == null ? new byte[0] : body);
        String timestampS = timestamp.toString();

        for (String nodeUrl : nodeUrls) {
            // if (nodeUrl.equals(serviceConfig.selfUrl())) {
            //     CompletableFuture.supplyAsync(() -> executeDaoOperation(id, request, timestamp)); //todo ну глина же
            // todo нормально не завернешь в респонс, да и код не обобщишь нифига. Сильно теряем производительность на 1 ноде
            // todo подумаю че еще сделать можно, много времени не тратил на это
            // } else {

            HttpRequest redirectRequest =
                HttpRequest.newBuilder(URI.create(nodeUrl + request.getURI()))
                    .method(
                        request.getMethodName(),
                        bodyPublisher
                    )
                    .header(TIMESTAMP_HEADER, timestampS)
                    .build();

            client.sendAsync(redirectRequest, HttpResponse.BodyHandlers.ofByteArray())
                .whenComplete((response, e) -> {
                    try {
                        int responded = atomicResponded.incrementAndGet();

                        if (response != null) {
                            log.debug(String.format(
                                "Redirected response from node: %s received. This node: %s. Key: %s. Status %s. Responded %s/%s",
                                nodeUrl, serviceConfig.selfUrl(), id, response.statusCode(), responded, from)
                            );


                            switch (request.getMethod()) {
                            case Request.METHOD_GET -> {
                                if (response.statusCode() == HTTP_NOT_FOUND) {
                                    int acked = atomicAcked.incrementAndGet();
                                    EntryWrapper last = atomicLastEntry.get();

                                    if (acked == ack) {
                                        if (last == null) {
                                            logAndSendClientResponse(responseNotFound(), session, nodeUrl);
                                        } else {
                                            logAndSendClientResponse(responseOk(last.bytes), session, nodeUrl);
                                        }
                                    }
                                }

                                if (response.statusCode() == HTTP_OK) {
                                    try {
                                        while (true) {
                                            EntryWrapper oldEntry = atomicLastEntry.get();
                                            EntryWrapper newEntry = (EntryWrapper) deserialize(response.body());

                                            if ((oldEntry == null || newEntry.compareTo(oldEntry) > 0)
                                                && !atomicLastEntry.compareAndSet(oldEntry, newEntry))
                                            {
                                                continue;
                                            }
                                            break;
                                        }
                                    } catch (IOException | ClassNotFoundException ex) {
                                        log.error("Error occurred while deserialization", ex);
                                    }

                                    int acked = atomicAcked.incrementAndGet();
                                    EntryWrapper last = atomicLastEntry.get();

                                    if (acked == ack) {
                                        if (last.isTombstone) {
                                            logAndSendClientResponse(responseNotFound(), session, nodeUrl);
                                        } else {
                                            logAndSendClientResponse(responseOk(last.bytes), session, nodeUrl);
                                        }
                                    }
                                }
                            }
                            case Request.METHOD_PUT -> {
                                if (response.statusCode() == HTTP_CREATED) {
                                    int acked = atomicAcked.incrementAndGet();

                                    if (acked == ack) {
                                        logAndSendClientResponse(responseCreated(), session, nodeUrl);
                                    }
                                }
                            }
                            case Request.METHOD_DELETE -> {
                                if (response.statusCode() == HTTP_ACCEPTED) {
                                    int acked = atomicAcked.incrementAndGet();

                                    if (acked == ack) {
                                        logAndSendClientResponse(responseAccepted(), session, nodeUrl);
                                    }
                                }
                            }
                            }
                        }

                        // the moment when we increment atomicFinished value to from, all threads finished section
                        // where they can change acked so two atomic reads in a row is ok
                        int finished = atomicFinished.incrementAndGet();
                        int acked = atomicAcked.get();

                        if (e != null) {
                            log.error(String.format(
                                "Connection error from node: %s received. This node: %s. Key: %s. Responded %s/%s",
                                nodeUrl, serviceConfig.selfUrl(), id, responded, from), e
                            );
                        }

                        if (finished == from && acked < ack) {
                            logClientResponse(responseNotEnoughReplicas(), session, nodeUrl);
                            session.sendResponse(responseNotEnoughReplicas());
                        }
                    } catch (IOException ex) {
                        log.error("Error occurred while responding to client", ex);
                    }
                });
        }
    }

    private static void logAndSendClientResponse(Response response, HttpSession session, String nodeUrl)
        throws IOException
    {
        logClientResponse(response, session, nodeUrl);
        session.sendResponse(response);
    }

    private static void logClientResponse(Response response, HttpSession session, String nodeUrl) {
        log.debug(String.format(
            "Send response: %s to session: %s from node: %s ",
            response.getStatus(), session, nodeUrl)
        );
    }

    @ServiceFactory(stage = 4, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new DaoService(config);
        }
    }
}
