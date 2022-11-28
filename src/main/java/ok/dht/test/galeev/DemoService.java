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

import static ok.dht.test.galeev.SkipOldExecutorFactory.shutdownAndAwaitTermination;

public class DemoService implements Service {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoService.class);
    private static final Duration CLIENT_TIMEOUT = Duration.of(500, ChronoUnit.MILLIS);
    public static final String DEFAULT_PATH = "/v0/entity";
    public static final String LOCAL_PATH = "/v0/local/entity";
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
        server.addRequestHandlers(DEFAULT_PATH,
                new int[]{Request.METHOD_PUT, Request.METHOD_GET, Request.METHOD_DELETE}, this::universalHandler);
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

    @SuppressWarnings("FutureReturnValueIgnored")
    public void universalHandler(Request request, HttpSession session) throws IOException {
        Header header = new Header(request, consistentHashRouter.getAmountOfPhysicalNodes());
        if (!header.isOk()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }
        Handler<?> handler = Handler.getHandler(request);

        AckBarrier barrier = new AckBarrier(header.getAck(), header.getFrom());

        List<Node> routerNode = consistentHashRouter.getNode(header.getKey(), header.getFrom());
        for (Node node : routerNode) {
            handler.action(node, header.getKey()).thenAcceptAsync((optional) -> {
                        if (optional.isEmpty()) {
                            handler.onError();
                            barrier.unSuccess();
                        } else {
                            handler.onSuccess(optional);
                            barrier.success();
                        }
                        if (barrier.isNeedToSendResponseToClient()) {
                            defaultResponse(session, handler, barrier);
                        }
                    },
                    proxyExecutor);
        }
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private void defaultResponse(HttpSession session, Handler<?> handler, AckBarrier barrier) {
        try {
            if (barrier.isAckAchieved()) {
                session.sendResponse(handler.responseOk());
            } else {
                session.sendResponse(handler.responseError());
            }
        } catch (IOException e) {
            LOGGER.error("Error, while sending response to client", e);
        } finally {
            handler.finishResponse();
        }
    }

    public void localHandleGet(Request request, HttpSession session) throws IOException {
        String key = request.getParameter(Header.ID_PARAMETER);

        Entry<Timestamp, byte[]> entry = localNode.getFromDao(key);
        if (entry.key() == null) {
            session.sendResponse(new Response(Response.NOT_FOUND, Response.EMPTY));
        } else {
            session.sendResponse(
                    new Response(Response.OK, Node.ClusterNode.entryToByteArray(entry.key(), entry.value()))
            );
        }
    }

    public void localHandlePutDelete(Request request, HttpSession session) throws IOException {
        String key = request.getParameter(Header.ID_PARAMETER);
        Entry<Timestamp, byte[]> entry = Node.ClusterNode.getEntryFromByteArray(request.getBody());

        localNode.putToDao(key, entry);
        if (entry.value() == null) {
            session.sendResponse(new Response(Response.ACCEPTED, Response.EMPTY));
        } else {
            session.sendResponse(new Response(Response.CREATED, Response.EMPTY));
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

    @ServiceFactory(stage = 5, week = 1, bonuses = {"SingleNodeTest#respectFileFolder"})
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new DemoService(config);
        }
    }
}
