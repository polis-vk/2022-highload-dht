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
    private final String LoggerPrefix;
    protected SkipOldExecutorFactory skipOldThreadExecutorFactory = new SkipOldExecutorFactory();
    private final ServiceConfig config;
    private DaoMiddleLayer<String, byte[]> dao;
    private ConsistentHashRouter consistentHashRouter;
    private CustomHttpServer server;
    private ClusterClient clusterClient;
    private ThreadPoolExecutor executorService;

    public DemoService(ServiceConfig config) {
        this.config = config;
        LoggerPrefix = config.selfUrl() + ": ";
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
        String key = request.getParameter("id=");
        ConsistentHashRouter.Node routerNode = consistentHashRouter.getNode(key);

        Response finalResponse;
        if (routerNode.isAlive) {
            if (routerNode.nodeAddress.equals(config.selfUrl())) {
                logInfo("dao get", request.getMethod(), key, null);
                Entry<String, byte[]> entry = dao.get(key);
                if (entry == null) {
                    finalResponse = new Response(Response.NOT_FOUND, Response.EMPTY);
                } else {
                    finalResponse = new Response(Response.OK, entry.value());
                }
            } else {
                logInfo("proxy send", request.getMethod(), key, routerNode);
                try {
                    finalResponse = clusterClient.get(routerNode, key);
                    logInfo("proxy got response", request.getMethod(), key, routerNode);
                } catch (ExecutionException | InterruptedException e) {
                    logError("proxy error while getting response",
                            request.getMethod(), key, routerNode, e);
                    finalResponse = new Response(Response.INTERNAL_ERROR, Response.EMPTY);
                } catch (TimeoutException e) {
                    logError("proxy time out while getting response",
                            request.getMethod(), key, routerNode, null);
                    finalResponse = new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
                }
            }
        } else {
            logInfo("cannot access router node is dead", request.getMethod(), key, routerNode);
            finalResponse = new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
        }
        session.sendResponse(finalResponse);
        logInfo("send response", request.getMethod(), key, null);
    }

    public void handlePut(Request request, HttpSession session) throws IOException {
        String key = request.getParameter("id=");
        ConsistentHashRouter.Node routerNode = consistentHashRouter.getNode(key);

        Response finalResponse;
        if (routerNode.isAlive) {
            if (routerNode.nodeAddress.equals(config.selfUrl())) {
                logInfo("dao put", request.getMethod(), key, null);
                dao.upsert(key, request.getBody());
                finalResponse = new Response(Response.CREATED, Response.EMPTY);
            } else {
                logInfo("proxy send", request.getMethod(), key, routerNode);
                try {
                    finalResponse = clusterClient.put(routerNode, key, request.getBody());
                    logInfo("proxy got response", request.getMethod(), key, routerNode);
                } catch (ExecutionException | InterruptedException e) {
                    logError("proxy error while getting response",
                            request.getMethod(), key, routerNode, e);
                    finalResponse = new Response(Response.INTERNAL_ERROR, Response.EMPTY);
                } catch (TimeoutException e) {
                    logError("proxy time out while getting response",
                            request.getMethod(), key, routerNode, null);
                    finalResponse = new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
                }
            }
        } else {
            logInfo("cannot access router node is dead", request.getMethod(), key, routerNode);
            finalResponse = new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
        }
        session.sendResponse(finalResponse);
        logInfo("send response", request.getMethod(), key, null);

    }

    public void handleDelete(Request request, HttpSession session) throws IOException {
        String key = request.getParameter("id=");
        ConsistentHashRouter.Node routerNode = consistentHashRouter.getNode(key);

        Response finalResponse;
        if (routerNode.isAlive) {
            if (routerNode.nodeAddress.equals(config.selfUrl())) {
                logInfo("dao response", request.getMethod(), key, null);
                dao.delete(key);
                finalResponse = new Response(Response.ACCEPTED, Response.EMPTY);
            } else {
                logInfo("proxy send", request.getMethod(), key, routerNode);
                try {
                    finalResponse = clusterClient.delete(routerNode, key);
                    logInfo("proxy got response", request.getMethod(), key, routerNode);
                } catch (ExecutionException | InterruptedException e) {
                    logError("proxy error while getting response",
                            request.getMethod(), key, routerNode, e);
                    finalResponse = new Response(Response.INTERNAL_ERROR, Response.EMPTY);
                } catch (TimeoutException e) {
                    logError("proxy time out while getting response",
                            request.getMethod(), key, routerNode, null);
                    finalResponse = new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
                }
            }
        } else {
            logInfo("cannot access router node is dead", request.getMethod(), key, routerNode);
            finalResponse = new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY);
        }
        session.sendResponse(finalResponse);
        logInfo("send response", request.getMethod(), key, null);
    }

    private void logInfo(String messageText, int method, String id, ConsistentHashRouter.Node routeNode) {
        LOGGER.info(getLogText(messageText, method, id, routeNode));
    }

    private void logError(String messageText, int method, String id, ConsistentHashRouter.Node routeNode, Exception e) {
        LOGGER.error(getLogText(messageText, method, id, routeNode), e);
    }

    private String getLogText(String messageText, int method, String id, ConsistentHashRouter.Node routeNode) {
        return LoggerPrefix + "\n"
                + "\t" + messageText + "\n"
                + "\t" + "Method: " + method + "\n"
                + "\t" + "Id: " + id + "\n"
                + "\t" + ((routeNode == null) ? "Locally" : "RouteNode: " + routeNode.nodeAddress);
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
