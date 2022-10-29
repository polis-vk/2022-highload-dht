package ok.dht.test.galeev;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.galeev.dao.entry.Entry;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

import static ok.dht.test.galeev.SkipOldExecutorFactory.shutdownAndAwaitTermination;

public class DemoService implements Service {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoService.class);
    private static final Duration CLIENT_TIMEOUT = Duration.of(300, ChronoUnit.MILLIS);
    public static final String DEFAULT_PATH = "/v0/entity";
    public static final String LOCAL_PATH = "/v0/local/entity";
    public static final String NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";
    private final ServiceConfig config;
    private final SkipOldExecutorFactory skipOldThreadExecutorFactory = new SkipOldExecutorFactory();
    private ExecutorService proxyExecutor;
    private ConsistentHashRouter consistentHashRouter;
    private Node.LocalNode localNode;
    private CustomHttpServer server;
    private ThreadPoolExecutor workersExecutor;

    public DemoService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        LOGGER.info("DemoService started with config:"
                + "\nCluster urls: " + config.clusterUrls()
                + " \nSelf url: " + config.selfUrl());
        workersExecutor = skipOldThreadExecutorFactory.getExecutor();

        proxyExecutor = Executors.newFixedThreadPool(8);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CLIENT_TIMEOUT)
                .executor(proxyExecutor)
                .build();

        consistentHashRouter = new ConsistentHashRouter();
        for (String nodeUrl : config.clusterUrls()) {
            if (nodeUrl.equals(config.selfUrl())) {
                localNode = new Node.LocalNode(config);
                consistentHashRouter.addPhysicalNode(localNode);
            } else {
                consistentHashRouter.addPhysicalNode(new Node.ClusterNode(nodeUrl, httpClient));
            }
        }

        server = new CustomHttpServer(createConfigFromPort(config.selfPort()), workersExecutor);
        server.addRequestHandlers(DEFAULT_PATH, new int[]{Request.METHOD_GET}, this::handleGet);
        server.addRequestHandlers(DEFAULT_PATH, new int[]{Request.METHOD_PUT}, this::handlePut);
        server.addRequestHandlers(DEFAULT_PATH, new int[]{Request.METHOD_DELETE}, this::handleDelete);
        server.addRequestHandlers(LOCAL_PATH, new int[]{Request.METHOD_GET}, this::localHandleGet);
        server.addRequestHandlers(LOCAL_PATH, new int[]{Request.METHOD_PUT}, this::localHandlePutDelete);
        server.start();
        LOGGER.debug(this.getClass().getName() + " Has started");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.prepareStopping();
        shutdownAndAwaitTermination(workersExecutor);
        shutdownAndAwaitTermination(proxyExecutor);
        localNode.stop();
        server.stop();
        return CompletableFuture.completedFuture(null);
    }

    public void handleGet(Request request, HttpSession session) throws IOException {
        Header header = new Header(request, consistentHashRouter.getAmountOfPhysicalNodes());
        if (!header.isOk()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
        }

        AckBarrier barrier = new AckBarrier(header.getAck(), header.getFrom());
        AtomicReference<Entry<Timestamp, byte[]>> newestEntry = new AtomicReference<>();

        List<Node> routerNode = consistentHashRouter.getNode(header.getKey(), header.getFrom());
        for (Node node : routerNode) {
            node.get(header.getKey()).thenAccept((entry) -> {
                if (entry == null) {
                    barrier.unSuccess();
                } else {
                    updateNewestEntry(newestEntry, entry);
                    barrier.success();
                }
            });
        }

        barrier.waitContinueBarrier(session,
                String.format("%nInterrupted while awaiting GET responses key: %s%n", header.getKey()));

        Entry<Timestamp, byte[]> entry = newestEntry.get();
        if (barrier.isAckAchieved()) {
            if (entry.key() == null || entry.value() == null) {
                session.sendResponse(new Response(Response.NOT_FOUND, Response.EMPTY));
            } else {
                session.sendResponse(new Response(Response.OK, entry.value()));
            }
        } else {
            session.sendResponse(new Response(NOT_ENOUGH_REPLICAS, Response.EMPTY));
        }
    }

    public void localHandleGet(Request request, HttpSession session) throws IOException {
        String key = request.getParameter(Header.ID_PARAMETR);

        Entry<Timestamp, byte[]> entry = localNode.getFromDao(key);
        if (entry.key() == null) {
            session.sendResponse(new Response(Response.NOT_FOUND, Response.EMPTY));
        } else {
            session.sendResponse(
                    new Response(Response.OK, Node.ClusterNode.entryToByteArray(entry.key(), entry.value()))
            );
        }
    }

    public void handlePut(Request request, HttpSession session) throws IOException {
        final Timestamp currentTime = new Timestamp(System.currentTimeMillis());

        Header header = new Header(request, consistentHashRouter.getAmountOfPhysicalNodes());
        if (!header.isOk()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
        }

        AckBarrier barrier = new AckBarrier(header.getAck(), header.getFrom());

        List<Node> routerNode = consistentHashRouter.getNode(header.getKey(), header.getFrom());
        for (Node node : routerNode) {
            node.put(header.getKey(), currentTime, request.getBody()).thenAccept(barrier.getDefaultSuccessChecker());
        }

        barrier.waitContinueBarrier(session,
                String.format("%nInterrupted while awaiting PUT responses key: %s%n", header.getKey()));

        if (barrier.isAckAchieved()) {
            session.sendResponse(new Response(Response.CREATED, Response.EMPTY));
        } else {
            session.sendResponse(new Response(NOT_ENOUGH_REPLICAS, Response.EMPTY));
        }
    }

    public void handleDelete(Request request, HttpSession session) throws IOException {
        final Timestamp currentTime = new Timestamp(System.currentTimeMillis());

        Header header = new Header(request, consistentHashRouter.getAmountOfPhysicalNodes());
        if (!header.isOk()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
        }

        AckBarrier barrier = new AckBarrier(header.getAck(), header.getFrom());

        List<Node> routerNode = consistentHashRouter.getNode(header.getKey(), header.getFrom());
        for (Node node : routerNode) {
            node.delete(header.getKey(), currentTime).thenAccept(barrier.getDefaultSuccessChecker());
        }

        barrier.waitContinueBarrier(session,
                String.format("%nInterrupted while awaiting DELETE responses key: %s%n", header.getKey()));

        if (barrier.isAckAchieved()) {
            session.sendResponse(new Response(Response.ACCEPTED, Response.EMPTY));
        } else {
            session.sendResponse(new Response(NOT_ENOUGH_REPLICAS, Response.EMPTY));
        }
    }

    public void localHandlePutDelete(Request request, HttpSession session) throws IOException {
        String key = request.getParameter(Header.ID_PARAMETR);
        Entry<Timestamp, byte[]> entry = Node.ClusterNode.getEntryFromByteArray(request.getBody());

        localNode.putToDao(key, entry);
        if (entry.value() == null) {
            session.sendResponse(new Response(Response.ACCEPTED, Response.EMPTY));
        } else {
            session.sendResponse(new Response(Response.CREATED, Response.EMPTY));
        }
    }

    private static void updateNewestEntry(
            AtomicReference<Entry<Timestamp, byte[]>> newestEntry,
            Entry<Timestamp, byte[]> entry) {
        // When NotFound entry is (null,null)
        if (entry.key() == null) {
            // If response was Not Found -> set entry only if there was nothing else.
            newestEntry.compareAndSet(null, entry);
        } else {
            boolean needToUpdate;
            Entry<Timestamp, byte[]> currentNewestEntry;
            do {
                needToUpdate = false;
                currentNewestEntry = newestEntry.get();
                if (currentNewestEntry == null || currentNewestEntry.key() == null
                        || isFirstMoreActual(entry, currentNewestEntry)) {
                    // If there is absolutely no entry
                    // Or if there is NotFound entry (anything is better than this two)
                    // Or if we have more actual entry
                    needToUpdate = true;
                }
            } while (needToUpdate && !newestEntry.compareAndSet(currentNewestEntry, entry));
        }
    }

    private static boolean isFirstMoreActual(Entry<Timestamp, byte[]> first,
                                             Entry<Timestamp, byte[]> second) {
        return first.key().after(second.key())
                || (first.key().equals(second.key()) && first.isTombstone());
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @ServiceFactory(stage = 4, week = 2, bonuses = {"SingleNodeTest#respectFileFolder"})
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new DemoService(config);
        }
    }
}
