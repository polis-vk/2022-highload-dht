package ok.dht.test.galeev;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.galeev.dao.DaoMiddleLayer;
import ok.dht.test.galeev.dao.entry.Entry;
import ok.dht.test.galeev.dao.utils.DaoConfig;
import ok.dht.test.galeev.dao.utils.StringByteConverter;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeoutException;

public class DemoService implements Service {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoService.class);
    public static final String DEFAULT_PATH = "/v0/entity";
    public static final int FLUSH_THRESHOLD_BYTES = 16777216; // 16MB
    private final String LOGGER_PREFIX;
    protected SkipOldExecutorFactory skipOldThreadExecutorFactory = new SkipOldExecutorFactory();
    private final ServiceConfig config;
    private DaoMiddleLayer<String, byte[]> dao;
    private ConsistentHashRouter consistentHashRouter;
    private CustomHttpServer server;
    private ClusterClient clusterClient;
    private ThreadPoolExecutor executorService;

    public DemoService(ServiceConfig config) {
        this.config = config;
        LOGGER_PREFIX = config.selfUrl() + ": ";
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        LOGGER.info("DemoService started with config:"
                + "\nCluster urls: " + config.clusterUrls()
                + " \nSelf url: " + config.selfUrl());
        clusterClient = new ClusterClient();
        dao = getDao(config);
        executorService = skipOldThreadExecutorFactory.getExecutor();

        consistentHashRouter = new ConsistentHashRouter();
        for (String nodeUrl : config.clusterUrls()) {
            consistentHashRouter.addPhysicalNode(new ConsistentHashRouter.Node(nodeUrl));
        }

        server = new CustomHttpServer(createConfigFromPort(config.selfPort()), executorService);
        server.addRequestHandlers(
                DEFAULT_PATH,
                new int[]{Request.METHOD_GET},
                this::handleGet
        );
        server.addRequestHandlers(
                DEFAULT_PATH,
                new int[]{Request.METHOD_PUT},
                this::handlePut
        );
        server.addRequestHandlers(
                DEFAULT_PATH,
                new int[]{Request.METHOD_DELETE},
                this::handleDelete
        );
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        executorService.shutdown();
        clusterClient.stop();
        dao.stop();
        return CompletableFuture.completedFuture(null);
    }

    public void handleGet(Request request, HttpSession session) throws IOException {
        String id = request.getParameter("id=");
        ConsistentHashRouter.Node routerNode = consistentHashRouter.getNode(id);

        Response finalResponse;
        if (!routerNode.isAlive) {
            logInfo("cannot access router node is dead", request.getMethod(), id, routerNode);
            finalResponse = new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
        } else if (routerNode.nodeAddress.equals(config.selfUrl())) {
            logInfo("dao get", request.getMethod(), id, null);
            Entry<String, byte[]> entry = dao.get(id);
            if (entry == null) {
                finalResponse = new Response(Response.NOT_FOUND, Response.EMPTY);
            } else {
                finalResponse = new Response(Response.OK, entry.value());
            }
        } else {
            logInfo("proxy send", request.getMethod(), id, routerNode);
            try {
                finalResponse = clusterClient.get(routerNode, id);
                logInfo("proxy got response", request.getMethod(), id, routerNode);
            } catch (ExecutionException | InterruptedException e) {
                logError("proxy error while getting response",
                        request.getMethod(), id, routerNode, e);
                finalResponse = new Response(Response.INTERNAL_ERROR, Response.EMPTY);
            } catch (TimeoutException e) {
                logError("proxy time out while getting response",
                        request.getMethod(), id, routerNode, null);
                finalResponse = new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
            }
        }
        session.sendResponse(finalResponse);
        logInfo("send response", request.getMethod(), id, null);
    }

    public void handlePut(Request request, HttpSession session) throws IOException {
        String id = request.getParameter("id=");
        ConsistentHashRouter.Node routerNode = consistentHashRouter.getNode(id);

        Response finalResponse;
        if (!routerNode.isAlive) {
            logInfo("cannot access router node is dead", request.getMethod(), id, routerNode);
            finalResponse = new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
        } else if (routerNode.nodeAddress.equals(config.selfUrl())) {
            logInfo("dao put", request.getMethod(), id, null);
            dao.upsert(id, request.getBody());
            finalResponse = new Response(Response.CREATED, Response.EMPTY);
        } else {
            logInfo("proxy send", request.getMethod(), id, routerNode);
            try {
                finalResponse = clusterClient.put(routerNode, id, request.getBody());
                logInfo("proxy got response", request.getMethod(), id, routerNode);
            } catch (ExecutionException | InterruptedException e) {
                logError("proxy error while getting response",
                        request.getMethod(), id, routerNode, e);
                finalResponse = new Response(Response.INTERNAL_ERROR, Response.EMPTY);
            } catch (TimeoutException e) {
                logError("proxy time out while getting response",
                        request.getMethod(), id, routerNode, null);
                finalResponse = new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
            }
        }
        session.sendResponse(finalResponse);
        logInfo("send response", request.getMethod(), id, null);

    }

    public void handleDelete(Request request, HttpSession session) throws IOException {
        String id = request.getParameter("id=");
        ConsistentHashRouter.Node routerNode = consistentHashRouter.getNode(id);

        Response finalResponse;
        if (!routerNode.isAlive) {
            logInfo("cannot access router node is dead", request.getMethod(), id, routerNode);
            finalResponse = new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
        } else if (routerNode.nodeAddress.equals(config.selfUrl())) {
            logInfo("dao response", request.getMethod(), id, null);
            dao.delete(id);
            finalResponse = new Response(Response.ACCEPTED, Response.EMPTY);
        } else {
            logInfo("proxy send", request.getMethod(), id, routerNode);
            try {
                finalResponse = clusterClient.delete(routerNode, id);
                logInfo("proxy got response", request.getMethod(), id, routerNode);
            } catch (ExecutionException | InterruptedException e) {
                logError("proxy error while getting response",
                        request.getMethod(), id, routerNode, e);
                finalResponse = new Response(Response.INTERNAL_ERROR, Response.EMPTY);
            } catch (TimeoutException e) {
                logError("proxy time out while getting response",
                        request.getMethod(), id, routerNode, null);
                finalResponse = new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
            }
        }
        session.sendResponse(finalResponse);
        logInfo("send response", request.getMethod(), id, null);
    }

    private void logInfo(String message, int method, String id, ConsistentHashRouter.Node routeNode) {
//        LOGGER.info(LOGGER_PREFIX + "\n"
//                + "\t" + message + "\n"
//                + "\t" + "Method: " + method + "\n"
//                + "\t" + "Id: " + id + "\n"
//                + "\t" + ((routeNode == null) ? "Locally" : "RouteNode: " + routeNode.nodeAddress)
//        );
    }

    private void logError(String message, int method, String id, ConsistentHashRouter.Node routeNode, Exception e) {
//        LOGGER.error(LOGGER_PREFIX + "\n"
//                        + "\t" + message + "\n"
//                        + "\t" + "Method: " + method + "\n"
//                        + "\t" + "Id: " + id + "\n"
//                        + "\t" + ((routeNode == null) ? "Locally" : "RouteNode: " + routeNode.nodeAddress)
//                , e
//        );
    }

    private static DaoMiddleLayer<String, byte[]> getDao(ServiceConfig config)
            throws IOException {
        if (!Files.exists(config.workingDir())) {
            Files.createDirectory(config.workingDir());
        }
        return new DaoMiddleLayer<>(
                new DaoConfig(
                        config.workingDir(),
                        FLUSH_THRESHOLD_BYTES //16MB
                ),
                new StringByteConverter()
        );
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @ServiceFactory(stage = 3, week = 1, bonuses = {"SingleNodeTest#respectFileFolder"})
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new DemoService(config);
        }
    }
}
